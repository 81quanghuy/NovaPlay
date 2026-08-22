#!/usr/bin/env bash
# Sinh SealedSecret cho prod từ một file env plaintext, rồi đăng ký chúng vào kustomization của
# từng service.
#
#   k8s/infra/scripts/seal-secrets.sh k8s/infra/prod-secrets.env
#
# SealedSecret là Secret đã được mã hoá bằng public key của controller sealed-secrets đang chạy
# TRONG cluster. Chỉ controller đó giải mã được, nên file kết quả AN TOÀN để commit vào Git —
# đó là điều kiện cần để sau này chuyển sang ArgoCD.
#
# ---------------------------------------------------------------------------
# BACKUP SEALING KEY — đọc phần này trước khi chạy lần đầu
# ---------------------------------------------------------------------------
# Toàn bộ SealedSecret trong repo chỉ giải mã được bằng private key nằm trong cluster. Mất cluster
# mà chưa backup key thì mọi file đã commit trở thành rác không thể phục hồi, và bạn phải tạo lại
# từ đầu mọi credential (DB, Redis, S3, JWT keypair — nghĩa là toàn bộ người dùng bị đăng xuất).
#
#   kubectl get secret -n kube-system \
#     -l sealedsecrets.bitnami.com/sealed-secrets-key -o yaml > sealed-secrets-key-BACKUP.yaml
#
# Cất file đó ở nơi KHÔNG phải repo này (password manager, ổ mã hoá offline).
# ---------------------------------------------------------------------------
#
# Yêu cầu: kubectl trỏ đúng cluster prod, kubeseal đã cài, controller sealed-secrets đang chạy.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/k8s/infra/prod-secrets.env}"
NAMESPACE="${NAMESPACE:-novaplay}"

command -v kubeseal >/dev/null 2>&1 || {
  echo "Thiếu kubeseal. Cài: https://github.com/bitnami-labs/sealed-secrets/releases" >&2
  exit 1
}

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Không tìm thấy $ENV_FILE" >&2
  echo "Tạo từ mẫu: cp k8s/infra/prod-secrets.env.example k8s/infra/prod-secrets.env" >&2
  echo "File này chứa credential thật và ĐÃ ĐƯỢC gitignore — không bao giờ commit nó." >&2
  exit 1
fi

# Kiểm tra controller có thật sự chạy chưa. Không có bước này, kubeseal sẽ fail với lỗi mạng
# mơ hồ và dễ bị hiểu nhầm là sai kubeconfig.
kubectl get deployment -n kube-system sealed-secrets-controller >/dev/null 2>&1 || {
  echo "Chưa thấy sealed-secrets-controller trong namespace kube-system." >&2
  echo "Cài trước: helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system" >&2
  exit 1
}

seal() {
  # seal <secret-name> <service-slug> <kubectl-create-args...>
  local secret_name="$1" svc="$2"; shift 2
  local out="$ROOT_DIR/k8s/$svc/sealedsecret.yaml"

  kubectl create secret generic "$secret_name" --namespace "$NAMESPACE" "$@" \
    --dry-run=client -o yaml \
    | kubeseal --format yaml --namespace "$NAMESPACE" \
    >> "$out"

  echo "  -> $secret_name"
}

declare -A SECRET_ARGS
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
  [[ "$line" != *"__"*"="* ]] && continue
  var_name="${line%%=*}"; var_value="${line#*=}"
  prefix="${var_name%%__*}"; key="${var_name#*__}"
  service_slug="$(echo "$prefix" | tr '[:upper:]' '[:lower:]' | tr '_' '-')"
  SECRET_ARGS["$service_slug"]+="--from-literal=${key}=${var_value} "
done < "$ENV_FILE"

# Xoá file cũ TRƯỚC vòng lặp: seal() dùng >> để gộp nhiều Secret vào một file cho cùng service.
for svc in "${!SECRET_ARGS[@]}"; do
  : > "$ROOT_DIR/k8s/$svc/sealedsecret.yaml"
done
: > "$ROOT_DIR/k8s/auth-service/sealedsecret.yaml"
: > "$ROOT_DIR/k8s/api-gateway/sealedsecret.yaml"

echo "Đang seal secret cho namespace '$NAMESPACE'..."
for svc in "${!SECRET_ARGS[@]}"; do
  # shellcheck disable=SC2086
  seal "${svc}-secrets" "$svc" ${SECRET_ARGS[$svc]}
done

# JWT keypair: nguồn là file, không phải literal — nên tách khỏi vòng lặp trên.
AUTH_KEYS="$ROOT_DIR/auth-service/src/main/resources/keys"
GW_CERTS="$ROOT_DIR/api-gateway/src/main/resources/certs"
seal auth-service-jwt-keys auth-service \
  --from-file=private.pem="$AUTH_KEYS/private.pem" \
  --from-file=public.pem="$AUTH_KEYS/public.pem"
seal api-gateway-jwt-public-key api-gateway \
  --from-file=public.pem="$GW_CERTS/public.pem"

# Đăng ký vào kustomization để `kubectl apply -k` nhìn thấy. Idempotent: kustomize edit bỏ qua
# nếu resource đã có.
for svc in "${!SECRET_ARGS[@]}" auth-service api-gateway; do
  f="$ROOT_DIR/k8s/$svc/sealedsecret.yaml"
  [[ -s "$f" ]] || continue
  if command -v kustomize >/dev/null 2>&1; then
    (cd "$ROOT_DIR/k8s/$svc" && kustomize edit add resource sealedsecret.yaml 2>/dev/null || true)
  else
    grep -q "sealedsecret.yaml" "$ROOT_DIR/k8s/$svc/kustomization.yaml" \
      || sed -i 's|^resources:|resources:\n  - sealedsecret.yaml|' "$ROOT_DIR/k8s/$svc/kustomization.yaml"
  fi
done

echo
echo "Xong. File sealedsecret.yaml AN TOÀN để commit."
echo "Kiểm tra lại trước khi commit — không được thấy giá trị plaintext nào:"
echo "  grep -r 'encryptedData' k8s/*/sealedsecret.yaml | head"

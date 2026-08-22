#!/usr/bin/env bash
# Đọc k8s/infra/dev-secrets.env (KHÔNG commit) và tạo/update k8s Secret cho từng service.
# Idempotent: dùng --dry-run=client -o yaml | kubectl apply -f -, an toàn chạy lại mỗi lần
# `tilt up`. Gọi bởi Tiltfile như một local_resource, không tự chạy tay trừ khi debug.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/k8s/infra/dev-secrets.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Không tìm thấy $ENV_FILE" >&2
  echo "Chạy: cp k8s/infra/dev-secrets.env.example k8s/infra/dev-secrets.env rồi điền giá trị." >&2
  exit 1
fi

declare -A SECRET_ARGS

while IFS= read -r line || [[ -n "$line" ]]; do
  # Bỏ dòng trống/comment.
  [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
  [[ "$line" != *"__"*"="* ]] && continue

  var_name="${line%%=*}"
  var_value="${line#*=}"
  prefix="${var_name%%__*}"
  key="${var_name#*__}"

  # AUTH_SERVICE -> auth-service, TRANSCODING_WORKER -> transcoding-worker
  service_slug="$(echo "$prefix" | tr '[:upper:]' '[:lower:]' | tr '_' '-')"

  SECRET_ARGS["$service_slug"]+="--from-literal=${key}=${var_value} "
done < "$ENV_FILE"

if [[ ${#SECRET_ARGS[@]} -eq 0 ]]; then
  echo "Không đọc được key nào từ $ENV_FILE — kiểm tra lại format SERVICE__KEY=value." >&2
  exit 1
fi

for service_slug in "${!SECRET_ARGS[@]}"; do
  secret_name="${service_slug}-secrets"
  # shellcheck disable=SC2086
  kubectl create secret generic "$secret_name" ${SECRET_ARGS[$service_slug]} \
    --dry-run=client -o yaml | kubectl apply -f -
done

# ---------------------------------------------------------------------------
# JWT keypair: hai Secret riêng, nguồn là file .pem trên máy dev (đã gitignore, preflight
# check-jwt-keys.sh đảm bảo chúng tồn tại trước khi tới đây).
#
# Trước đây key được BAKE vào image lúc build (COPY src). Cách đó có hai vấn đề: image do CI
# build từ checkout sạch sẽ KHÔNG có key (vì *.pem bị gitignore), và image build ở máy local thì
# mang theo private key vĩnh viễn trong layer. Nay cả dev lẫn prod đều mount từ Secret — dev đi
# đúng đường mà prod đi, chỉ khác nguồn sinh Secret (script này ở dev, kubeseal ở prod).
# ---------------------------------------------------------------------------
AUTH_KEYS_DIR="$ROOT_DIR/auth-service/src/main/resources/keys"
GATEWAY_CERTS_DIR="$ROOT_DIR/api-gateway/src/main/resources/certs"

kubectl create secret generic auth-service-jwt-keys \
  --from-file=private.pem="$AUTH_KEYS_DIR/private.pem" \
  --from-file=public.pem="$AUTH_KEYS_DIR/public.pem" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic api-gateway-jwt-public-key \
  --from-file=public.pem="$GATEWAY_CERTS_DIR/public.pem" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Đã apply ${#SECRET_ARGS[@]} Secret: ${!SECRET_ARGS[*]}"
echo "Đã apply 2 Secret JWT: auth-service-jwt-keys, api-gateway-jwt-public-key"

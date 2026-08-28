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

# service_slug -> danh sách key, phân tách bằng newline (KHÔNG dùng dấu cách: giá trị secret có
# thể chứa dấu cách). Giá trị tra riêng qua SECRET_VALUES["slug|KEY"].
declare -A SECRET_KEYS
declare -A SECRET_VALUES

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

  SECRET_KEYS["$service_slug"]+="${key}"$'\n'
  SECRET_VALUES["${service_slug}|${key}"]="$var_value"
done < "$ENV_FILE"

if [[ ${#SECRET_KEYS[@]} -eq 0 ]]; then
  echo "Không đọc được key nào từ $ENV_FILE — kiểm tra lại format SERVICE__KEY=value." >&2
  exit 1
fi

for service_slug in "${!SECRET_KEYS[@]}"; do
  secret_name="${service_slug}-secrets"

  # Credential Grafana Cloud thuộc về Alloy, vốn nằm ở namespace monitoring chứ không phải
  # default như 9 service ứng dụng. Tạo luôn namespace ở đây thay vì phụ thuộc thứ tự resource
  # của Tilt: local_resource này không có cách nào chờ `monitoring-namespace` mà không tạo vòng
  # phụ thuộc với chính resource alloy (alloy cần secret, secret cần namespace).
  ns_args=()
  if [[ "$service_slug" == "grafana-cloud" ]]; then
    # Tạo kèm LABEL name=monitoring, không dùng `kubectl create namespace` trơn: NetworkPolicy
    # của 9 service chỉ cho scrape từ namespace có label đó (k8s/infra/monitoring/namespace.yaml),
    # và trên cluster có CNI thi hành NetworkPolicy — k3s dùng kube-router — namespace thiếu label
    # nghĩa là Alloy scrape bị chặn im lặng, Grafana Cloud không có metric nào.
    kubectl apply --validate=false -f - >/dev/null <<'NS_EOF'
apiVersion: v1
kind: Namespace
metadata:
  name: monitoring
  labels:
    name: monitoring
NS_EOF
    ns_args=(--namespace monitoring)
  fi

  # Mảng thật, KHÔNG phải chuỗi: giá trị như sasl.jaas.config hay URL có tham số đều có thể chứa
  # dấu cách, chuỗi expand không quote sẽ bị tách thành nhiều tham số và Secret ra sai lặng lẽ.
  args=()
  while IFS= read -r key; do
    [[ -z "$key" ]] && continue
    args+=("--from-literal=${key}=${SECRET_VALUES["${service_slug}|${key}"]}")
  done <<< "${SECRET_KEYS[$service_slug]}"

  kubectl create secret generic "$secret_name" "${ns_args[@]}" "${args[@]}" \
    --dry-run=client -o yaml | kubectl apply --validate=false -f -
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
  --dry-run=client -o yaml | kubectl apply --validate=false -f -

kubectl create secret generic api-gateway-jwt-public-key \
  --from-file=public.pem="$GATEWAY_CERTS_DIR/public.pem" \
  --dry-run=client -o yaml | kubectl apply --validate=false -f -

# ---------------------------------------------------------------------------
# CA của Aiven Kafka: một Secret DÙNG CHUNG cho cả 5 service chạm Kafka (auth, media,
# notification, user, transcoding-worker) — khác các Secret trên vốn mỗi service một cái, vì đây
# là chứng chỉ công khai của broker, không phải credential riêng của service nào.
#
# Broker Aiven dùng CA riêng của từng project, JVM KHÔNG tin sẵn: thiếu file này thì lỗi hiện ra
# là SSLHandshakeException lúc bắt tay TLS chứ không phải lỗi xác thực, rất dễ đi debug nhầm
# hướng username/password. Tải ở Aiven Console -> service Kafka -> Connect information ->
# CA certificate -> Show/Download, lưu thành đúng đường dẫn dưới đây.
# ---------------------------------------------------------------------------
KAFKA_CA_FILE="$ROOT_DIR/k8s/infra/aiven-kafka-ca.pem"

if [[ -f "$KAFKA_CA_FILE" ]]; then
  kubectl create secret generic aiven-kafka-ca \
    --from-file=ca.pem="$KAFKA_CA_FILE" \
    --dry-run=client -o yaml | kubectl apply --validate=false -f -
  echo "Đã apply Secret aiven-kafka-ca (nguồn: $KAFKA_CA_FILE)"
else
  # Không fail: cụm chạy Kafka in-cluster (use_cloud_kafka=false) không cần CA nào cả, và Pod
  # vẫn khởi động được vì volume kafka-ca khai báo optional: true.
  echo "BỎ QUA Secret aiven-kafka-ca — chưa có $KAFKA_CA_FILE." >&2
  echo "  Bắt buộc phải có nếu tilt-settings.json đang để use_cloud_kafka=true," >&2
  echo "  nếu không 5 service dùng Kafka sẽ chết ở SSLHandshakeException." >&2
fi

echo "Đã apply ${#SECRET_KEYS[@]} Secret: ${!SECRET_KEYS[*]}"
echo "Đã apply 2 Secret JWT: auth-service-jwt-keys, api-gateway-jwt-public-key"

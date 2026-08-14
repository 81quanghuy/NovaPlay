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

echo "Đã apply ${#SECRET_ARGS[@]} Secret: ${!SECRET_ARGS[*]}"

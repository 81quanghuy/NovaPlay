#!/usr/bin/env bash
# Preflight: auth-service/api-gateway KHÔNG tạo JWT keypair lúc chạy — key được bake vào image
# lúc build (COPY src, .pem nằm trong resources, gitignore bởi "*.pem"). Thiếu file thì image vẫn
# build "thành công" nhưng service crash hoặc verify JWT sai lúc chạy. Fail sớm ở đây thay vì để
# lỗi mơ hồ xuất hiện sau khi đã tốn thời gian build/deploy.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

missing=()
for f in \
  "auth-service/src/main/resources/keys/private.pem" \
  "auth-service/src/main/resources/keys/public.pem" \
  "api-gateway/src/main/resources/certs/public.pem"
do
  [[ -f "$ROOT_DIR/$f" ]] || missing+=("$f")
done

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "Thiếu JWT keypair, không thể build image auth-service/api-gateway:" >&2
  for f in "${missing[@]}"; do echo "  - $f" >&2; done
  echo "Xem k8s/infra/README.md mục 'Yêu cầu' để biết cách tạo cặp key RSA." >&2
  exit 1
fi

echo "JWT keypair OK."

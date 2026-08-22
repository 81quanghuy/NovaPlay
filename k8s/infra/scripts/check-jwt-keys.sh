#!/usr/bin/env bash
# Preflight: auth-service/api-gateway KHÔNG tạo JWT keypair lúc chạy.
#
# Key KHÔNG còn được bake vào image (xem auth-service/.dockerignore và api-gateway/.dockerignore
# — chúng loại *.pem khỏi build context). Thay vào đó apply-dev-secrets.sh đọc chính ba file dưới
# đây để tạo Secret auth-service-jwt-keys / api-gateway-jwt-public-key, rồi Pod mount chúng vào
# /etc/novaplay/keys. Đây đúng là đường mà prod đi, chỉ khác nguồn sinh Secret (kubeseal ở prod).
#
# Thiếu file thì Secret rỗng và service crash lúc khởi động. Fail sớm ở đây thay vì để lỗi mơ hồ
# xuất hiện sau khi đã tốn thời gian build/deploy.
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

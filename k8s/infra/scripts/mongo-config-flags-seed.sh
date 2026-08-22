#!/usr/bin/env bash
# Seed giá trị mặc định cho config_service.config_flags — mirror "config-service-init" của
# docker-compose/qa/docker-compose.yml. Idempotent (upsert:true, updateOne với $setOnInsert):
# không ghi đè nếu key đã tồn tại. KHÔNG bắt buộc cho tính đúng đắn — nếu thiếu,
# ConfigServiceFeatureProvider tự fallback về default phía media-service.
#
# Tham số 1: giá trị cờ media-storage-provider (aws-s3 | cloudflare-r2 | backblaze-b2).
# Tiltfile truyền vào theo use_cloud_storage. LƯU Ý $setOnInsert: nếu document đã tồn tại từ lần
# seed trước với giá trị cũ thì chạy lại script này KHÔNG đổi được gì — phải $set thủ công (xem
# k8s/infra/README.md mục R2). Script này cũng KHÔNG chạy khi use_cloud_mongo=true (Mongo Atlas
# không nằm trong cụm), lúc đó phải chạy lệnh mongosh tương đương ở README.
set -euo pipefail

STORAGE_PROVIDER="${1:-aws-s3}"

kubectl rollout status deployment/mongodb --timeout=120s

kubectl exec -i deploy/mongodb -- mongosh --quiet \
  "mongodb://localhost:27017/config_service?replicaSet=rs0" \
  --eval '
    db.config_flags.updateOne(
      { _id: "media-storage-provider" },
      { $setOnInsert: { value: "'"$STORAGE_PROVIDER"'" } },
      { upsert: true }
    )
  '

echo "Đã seed config_flags cho config-service (media-storage-provider=$STORAGE_PROVIDER)."

#!/usr/bin/env bash
# Seed giá trị mặc định cho config_service.config_flags — mirror "config-service-init" của
# docker-compose/qa/docker-compose.yml. Idempotent (upsert:true, updateOne với $setOnInsert):
# không ghi đè nếu key đã tồn tại. KHÔNG bắt buộc cho tính đúng đắn — nếu thiếu,
# ConfigServiceFeatureProvider tự fallback về default phía media-service.
set -euo pipefail

kubectl rollout status deployment/mongodb --timeout=120s

kubectl exec -i deploy/mongodb -- mongosh --quiet \
  "mongodb://localhost:27017/config_service?replicaSet=rs0" \
  --eval '
    db.config_flags.updateOne(
      { _id: "media-storage-provider" },
      { $setOnInsert: { value: "aws-s3" } },
      { upsert: true }
    )
  '

echo "Đã seed config_flags mặc định cho config-service."

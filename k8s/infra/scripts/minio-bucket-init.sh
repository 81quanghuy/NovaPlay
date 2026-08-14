#!/usr/bin/env bash
# Tạo bucket "novaplay-media" trên media-minio in-cluster — mirror "media-minio-init" của
# docker-compose/qa/docker-compose.yml. MinIO không tự tạo bucket lúc khởi động.
# Tên bucket khớp AWS_S3_BUCKET_NAME đã có sẵn trong k8s/media-service/configmap.yaml và
# k8s/streaming-service/configmap.yaml (KHÔNG dùng hậu tố "-dev" như compose).
# Idempotent: "mc mb -p" không lỗi nếu bucket đã tồn tại.
set -euo pipefail

kubectl rollout status deployment/media-minio --timeout=120s

kubectl run minio-mc-init --rm -i --restart=Never --image=minio/mc:latest --command -- \
  sh -c "mc alias set local http://media-minio:9000 media-dev media-dev-secret && mc mb -p local/novaplay-media"

echo "Đã đảm bảo bucket novaplay-media tồn tại trên media-minio."

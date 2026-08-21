#!/usr/bin/env bash
# Nạp lại schema + seed vào Postgres in-cluster. Idempotent (SQL dùng CREATE TABLE IF NOT EXISTS
# / ON CONFLICT — xem 2 file .sql), phải chạy lại mỗi khi pod postgres bị tạo mới vì chart cài
# với persistence.enabled=false (k8s/infra/postgresql-values.yaml). Gọi bởi Tiltfile.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

kubectl rollout status statefulset/postgres --timeout=180s

kubectl exec -i postgres-0 -- env PGPASSWORD=admin123 psql -U admin -d auth_service \
  < "$ROOT_DIR/k8s/infra/postgres-initdb.sql"
kubectl exec -i postgres-0 -- env PGPASSWORD=admin123 psql -U admin -d auth_service \
  < "$ROOT_DIR/k8s/infra/postgres-seed.sql"

echo "Đã nạp schema + seed vào Postgres (auth_service)."

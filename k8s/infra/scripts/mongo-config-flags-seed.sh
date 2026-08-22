#!/usr/bin/env bash
# Seed document mặc định cho collection config_flags của config-service — mirror
# "config-service-init" của docker-compose/qa/docker-compose.yml.
#
# Tham số 1: giá trị cờ media-storage-provider (aws-s3 | cloudflare-r2 | backblaze-b2).
# Tham số 2: "cloud" (Mongo Atlas) hay "in-cluster" (pod mongodb do Tilt deploy).
# Tiltfile truyền cả hai theo use_cloud_storage / use_cloud_mongo.
#
# Idempotent bằng $setOnInsert: KHÔNG ghi đè nếu document đã tồn tại. Nghĩa là đổi cờ bằng tay
# trên Atlas sẽ không bị lần `tilt up` sau xoá mất — nhưng cũng nghĩa là đổi
# use_cloud_storage rồi chạy lại script sẽ KHÔNG sửa được giá trị cũ. Script in ra giá trị thực
# tế sau khi chạy, nên lệch là thấy ngay.
#
# Vì sao cần: media-service hỏi cờ này qua OpenFeature (StorageProviderResolver) để chọn provider
# cho MỖI upload mới. Không có document, nó rơi về DEFAULT_STORAGE_PROVIDER — vẫn chạy, nhưng
# cờ trong DB mới là thứ THẮNG khi cả hai cùng tồn tại, nên để lệch nhau là upload chui vào
# provider không có credential.
set -euo pipefail

STORAGE_PROVIDER="${1:-aws-s3}"
MODE="${2:-in-cluster}"

if [[ "$MODE" == "in-cluster" ]]; then
  kubectl rollout status deployment/mongodb --timeout=120s

  kubectl exec -i deploy/mongodb -- mongosh --quiet \
    "mongodb://localhost:27017/config_service?replicaSet=rs0" \
    --eval '
      db.config_flags.updateOne(
        { _id: "media-storage-provider" },
        { $setOnInsert: { value: "'"$STORAGE_PROVIDER"'" } },
        { upsert: true }
      );
      print("media-storage-provider = " + db.config_flags.findOne({_id: "media-storage-provider"}).value);
    '
  echo "Đã seed config_flags (Mongo in-cluster)."
  exit 0
fi

# --- Mongo cloud (Atlas) ---------------------------------------------------------------------
# Không có pod mongodb nào trong cụm để exec vào, và mongosh cũng không chắc có trên máy dev.
# Chạy một Job dùng image mongo chính thức ngay trong cụm.
#
# URI (kèm password) đến từ Secret qua envFrom, KHÔNG truyền bằng tham số dòng lệnh: tham số sẽ
# lộ ra trong `kubectl get pod -o yaml`, trong log của Tilt và trong lịch sử shell.
JOB=mongo-config-seed

kubectl delete job "$JOB" --ignore-not-found >/dev/null

kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: $JOB
  labels:
    app: mongo-config-seed
spec:
  # Tự dọn sau 2 phút để lần tilt up sau không vướng job cũ.
  ttlSecondsAfterFinished: 120
  backoffLimit: 2
  template:
    metadata:
      labels:
        app: mongo-config-seed
    spec:
      restartPolicy: Never
      containers:
        - name: seed
          image: mongo:7
          envFrom:
            - secretRef:
                name: config-service-secrets
          command: ["mongosh", "--nodb", "--quiet", "--eval"]
          args:
            - |
              const d = connect(process.env.MONGODB_URI);
              d.config_flags.updateOne(
                { _id: "media-storage-provider" },
                { \$setOnInsert: { value: "$STORAGE_PROVIDER" } },
                { upsert: true }
              );
              print("media-storage-provider = " + d.config_flags.findOne({_id: "media-storage-provider"}).value);
EOF

if ! kubectl wait --for=condition=complete "job/$JOB" --timeout=180s; then
  echo "Job seed KHÔNG hoàn tất. Log:" >&2
  kubectl logs "job/$JOB" --tail=30 >&2 || true
  echo "Kiểm tra: IP của máy đã được allowlist trên Atlas chưa (Network Access), và" >&2
  echo "CONFIG_SERVICE__MONGODB_URI trong dev-secrets.env có đúng user/password không." >&2
  exit 1
fi

kubectl logs "job/$JOB" --tail=5
echo "Đã seed config_flags (Mongo Atlas)."

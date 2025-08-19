#!/bin/sh
# entrypoint.sh - Public Repository Version

echo "--- [DEBUG] Starting entrypoint script ---"

CLONE_DIR="/opt/cloud-config-repo-novaPlay"

echo "--- [DEBUG] Attempting to clone public repository ---"

# Xóa thư mục cũ nếu tồn tại
rm -rf ${CLONE_DIR}

# Clone repository public, không cần xác thực
# Thay đổi URL để trỏ trực tiếp vào repository
git clone "https://github.com/81quanghuy/cloud_config_NovaPlay.git" "${CLONE_DIR}" 2>&1
CLONE_EXIT_CODE=$?

if [ ${CLONE_EXIT_CODE} -eq 0 ]; then
  echo "--- [SUCCESS] Git clone was successful. ---"
else
  echo "--- [FAILURE] Git clone failed with exit code ${CLONE_EXIT_CODE}. Check if the repository is public. ---"
  # Thoát nếu không clone được vì ứng dụng sẽ không thể chạy đúng
  exit 1
fi

echo "--- [DEBUG] Starting Java application ---"
exec java -jar /app.jar
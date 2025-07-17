#!/bin/sh
# entrypoint.sh - Debugging version

echo "--- [DEBUG] Starting entrypoint script ---"

# Đọc secrets và xóa ký tự xuống dòng
export GITHUB_USERNAME=$(cat /run/secrets/github_username | tr -d '\n')
export GITHUB_TOKEN=$(cat /run/secrets/github_token | tr -d '\n')
CLONE_DIR="/opt/cloud-config-repo-novaPlay"

echo "--- [DEBUG] Attempting to clone repository manually using git command-line ---"

# Xóa thư mục cũ nếu tồn tại để đảm bảo clone mới hoàn toàn
rm -rf ${CLONE_DIR}

# Sử dụng token trong URL để xác thực
# Chuyển hướng output lỗi vào output chuẩn để docker-compose logs có thể thấy được
git clone "https://_:${GITHUB_TOKEN}@github.com/81quanghuy/cloud_config_NovaPlay.git" "${CLONE_DIR}" 2>&1
CLONE_EXIT_CODE=$?

if [ ${CLONE_EXIT_CODE} -eq 0 ]; then
  echo "--- [SUCCESS] Manual git clone was successful. ---"
else
  echo "--- [FAILURE] Manual git clone failed with exit code ${CLONE_EXIT_CODE}. Check log above for git errors. ---"
  # Vẫn tiếp tục để xem ứng dụng Java có báo lỗi khác không
fi

echo "--- [DEBUG] Starting Java application ---"
exec java -jar /app.jar

#!/bin/sh

set -e

# Kiểm tra secret db_name có tồn tại không
if [ ! -f /run/secrets/db_name ]; then
  echo "❌ db_name secret file is missing!"
  exit 1
fi

# Đọc secret để lấy tên DB
DB_NAME=$(cat /run/secrets/db_name)

echo "✅ Loaded DB name, creating JDBC URL..."

# Gán biến môi trường cho JDBC URL.
# Spring Boot sẽ tự động sử dụng USERNAME_FILE và PASSWORD_FILE đã định nghĩa trong docker-compose.
export SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/${DB_NAME}"

echo "🚀 Starting user-service..."

# Chạy ứng dụng với tên file JAR đã sửa
exec java -jar /app/app.jar
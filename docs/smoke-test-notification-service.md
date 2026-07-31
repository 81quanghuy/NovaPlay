# Smoke test — notification-service

Danh sách kiểm tra thủ công chạy trên hạ tầng thật trước khi phát hành. Unit test không thay thế
được nó: những lỗi nghiêm trọng nhất của service này — DLT không publish được, chống trùng vô tác
dụng, email tiếng Anh gửi cho người dùng Việt — đều xanh trong CI và chỉ lộ ra khi chạy thật.

## Chuẩn bị

```bash
docker compose -f docker-compose/qa/docker-compose.yml up -d mongodb redis kafka-services kafka-ui mailhog
./mvnw -q install -N && ./mvnw -q install -pl utils -DskipTests
./mvnw spring-boot:run -pl notification-service -Dspring-boot.run.profiles=dev
```

Muốn kiểm tra cả đường đi qua gateway thì chạy thêm `discovery-server` rồi `api-gateway`.

```bash
SVC=http://localhost:8900      # gọi thẳng service
GW=http://localhost:8072       # qua gateway
MAILHOG=http://localhost:8025  # hộp thư giả
KAFKA_UI=http://localhost:8080
ALICE='-H "X-User-Email: alice@novaplay.vn" -H "X-User-Roles: [ROLE_USER]"'
BOB='-H "X-User-Email: bob@novaplay.vn" -H "X-User-Roles: [ROLE_USER]"'
```

---

## 0. Cutover từ email-service — làm TRƯỚC khi triển khai

> Bỏ qua mục này nếu đây là môi trường chưa từng chạy `email-service`.

| # | Kiểm tra | Cách làm | Kỳ vọng |
|---|---|---|---|
| 0.1 | Consumer group cũ đã tiêu thụ hết | Kafka UI → Consumers → `email-service` | Lag = 0 trên `send-email.v1` |
| 0.2 | Dừng hẳn email-service | `docker stop email-service` (nếu còn chạy) | Container đã dừng |
| 0.3 | Mới start notification-service | | |

Thứ tự này là bắt buộc. Group mới (`notification-service`) bắt đầu từ `auto.offset.reset=latest`,
nên message còn tồn đọng trong lúc chuyển giao sẽ **không** được xử lý. Đặt `earliest` cũng không
phải lời giải — nó sẽ phát lại toàn bộ OTP lịch sử và gửi email thật cho người dùng thật.

---

## 1. Khởi động và sức khoẻ

| # | Kiểm tra | Lệnh | Kỳ vọng |
|---|---|---|---|
| 1.1 | Service khởi động | log ứng dụng | Không có exception, có dòng `MongoDB index verification completed for notifications` |
| 1.2 | Liveness | `curl -s $SVC/actuator/health/liveness` | `"status":"UP"` |
| 1.3 | Readiness gồm cả index | `curl -s $SVC/actuator/health/readiness` | `UP`, chi tiết có `mongo`, `redis`, `mongoIndex` |
| 1.4 | **Khởi động lại lần hai vẫn được** | Ctrl-C rồi chạy lại | Khởi động thành công — bài kiểm tra cho lỗi 85 `IndexOptionsConflict` |
| 1.5 | Index đã tạo đúng | `mongosh notification_service --eval 'db.notifications.getIndexes()'` | Có `idx_notification_user_created`, `idx_notification_user_unread` (partial), `idx_notification_expires_ttl` (`expireAfterSeconds: 0`) |
| 1.6 | Topic DLT đã tồn tại | Kafka UI → Topics | Có `send-email.v1.DLT`, `activate-account.v1.DLT`, `notification.requested.v1.DLT` |
| 1.7 | Đăng ký Eureka | mở `http://localhost:8761` | `NOTIFICATION-SERVICE` ở trạng thái UP |

## 2. Phân quyền và ranh giới dữ liệu

Gọi qua gateway (`$GW`) — phân quyền là kết quả phối hợp giữa gateway và service.

| # | Kiểm tra | Lệnh | Kỳ vọng |
|---|---|---|---|
| 2.1 | Ẩn danh bị chặn | `curl -si $GW/api/v1/notifications` | **401**, không phải 403 (FE chỉ refresh token khi thấy 401) |
| 2.2 | Người dùng đọc được của mình | `curl -s $GW/api/v1/notifications -H "Authorization: Bearer <jwt-alice>"` | 200, chỉ chứa thông báo của Alice |
| 2.3 | **Không đọc được của người khác** | Alice lấy một `id` của Bob rồi `PATCH $GW/api/v1/notifications/<id-cua-bob>/read` | **404** (không phải 403 — 403 sẽ tiết lộ rằng thông báo đó tồn tại) |
| 2.4 | Gọi thẳng service bị chặn ở PROD | bật `application.security.gateway-secret.enabled=true`, rồi `curl -si $SVC/api/v1/notifications` | **403** |
| 2.5 | Secret sai bị chặn | `curl -si $SVC/api/v1/notifications -H "X-Gateway-Auth: sai"` | **403** |

## 3. Luồng sự kiện end-to-end

Publish bằng Kafka UI (Topics → Produce Message) hoặc `kafka-console-producer`.

| # | Topic | Payload | Kỳ vọng |
|---|---|---|---|
| 3.1 | `send-email.v1` | `{"messageId":"m1","userId":"u1","email":"alice@novaplay.vn","variables":{"otp":"123456","expireMinutes":"5","locale":"vi-VN"}}` | 1 email ở $MAILHOG, tiêu đề **tiếng Việt**, thân thư có `123456` và "hiệu lực trong 5 phút". **0** document trong Mongo (OTP không vào in-app) |
| 3.2 | `send-email.v1` locale en | như trên nhưng `"locale":"en"` | Tiêu đề `Your OTP verification code` |
| 3.3 | `activate-account.v1` | `{"username":"alice","email":"alice@novaplay.vn"}` | 1 email chào mừng **và** 1 document trong `notifications` |
| 3.4 | `notification.requested.v1` | `{"messageId":"m2","userEmail":"alice@novaplay.vn","type":"GENERIC","channels":["IN_APP"],"locale":"vi-VN","variables":{"title":"Test"}}` | Chỉ 1 document in-app, không có email |
| 3.5 | Link trong email đúng cấu hình | mở email OTP ở $MAILHOG | Link trỏ tới `APP_FRONTEND_BASE_URL`, **không** phải `localhost:3000` hardcode |

## 4. Chống trùng và topic chết

| # | Kiểm tra | Cách làm | Kỳ vọng |
|---|---|---|---|
| 4.1 | Trùng `messageId` | Publish lại **y hệt** payload 3.1 | **Không** có email thứ hai. Log có `Bỏ qua trùng: kênh=EMAIL`. `notification_channel_skipped_total` tăng |
| 4.2 | Trùng ở kênh in-app | Publish lại payload 3.3 | Vẫn đúng 1 document (`_id` trùng bị Mongo chặn) |
| 4.3 | Kích hoạt lại cùng email | Publish 3.3 với offset khác | Không có email thứ hai — khoá nghiệp vụ theo email, không theo toạ độ bản ghi |
| 4.4 | **Payload hỏng vào DLT** | Publish `{"messageId":"bad","email":null,"variables":{}}` lên `send-email.v1` | Message xuất hiện ở `send-email.v1.DLT` trong Kafka UI. Log ERROR `Message rơi vào topic chết`. `notification_kafka_event_dlt_total` tăng. **Không** retry 5 lần (validation là lỗi non-retryable) |
| 4.5 | **Fail một phần không gửi lại kênh đã thành công** | Dừng MailHog, publish 3.3, đợi retry, bật lại MailHog | Cuối cùng đúng **1** email và **1** document — không phải nhiều bản sao |

## 5. Vận hành

| # | Kiểm tra | Lệnh | Kỳ vọng |
|---|---|---|---|
| 5.1 | Metrics cần xác thực | `curl -si $SVC/actuator/prometheus` | **401** khi không có header danh tính |
| 5.2 | Metrics đọc được khi có header | `curl -s $SVC/actuator/prometheus -H "X-User-Email: prometheus-scraper@internal" \| grep notification_` | Thấy `notification_channel_sent_total`, `notification_event_processed_total` |
| 5.3 | Prometheus scrape được | mở `http://localhost:9090/targets` | Job `notification-service` ở trạng thái UP |
| 5.4 | traceId có trong log | xem log bất kỳ dòng nào | Có dạng `[notification-service,<32 hex>,<16 hex>]` |
| 5.5 | Mất Mongo → readiness DOWN | `docker stop mongodb`, `curl -s $SVC/actuator/health/readiness` | readiness **DOWN**, liveness vẫn **UP** (pod không bị restart oan) |
| 5.6 | Mất Kafka **không** làm DOWN | `docker stop kafka-services`, kiểm tra readiness | Vẫn **UP** — REST API đọc thông báo không phụ thuộc broker |
| 5.7 | Dừng êm | gửi SIGTERM | Request đang chạy hoàn tất, consumer commit offset, không có lỗi trong log |
| 5.8 | Image build được | `docker build -f notification-service/Dockerfile .` | Build thành công |
| 5.9 | Swagger qua gateway | mở `$GW/swagger/notification/v3/api-docs` | Trả về JSON OpenAPI |

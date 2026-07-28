# Smoke test — movie-service

Danh sách kiểm tra thủ công chạy trên hạ tầng thật trước khi phát hành. Unit test không thay thế
được nó: bốn lỗi nghiêm trọng nhất của user-service (index conflict, DLQ hỏng, `@Builder.Default`,
mất auditing) đều xanh trong CI và chỉ lộ ra khi chạy service thật.

## Chuẩn bị

```bash
docker compose -f docker-compose/qa/docker-compose.yml up -d mongodb redis
./mvnw -q install -N && ./mvnw -q install -pl utils -DskipTests
./mvnw spring-boot:run -pl movie-service -Dspring-boot.run.profiles=dev
```

Muốn kiểm tra cả đường đi qua gateway thì chạy thêm `discovery-server` rồi `api-gateway`.

Đặt sẵn biến cho gọn:

```bash
SVC=http://localhost:8600      # gọi thẳng service
GW=http://localhost:8072       # qua gateway
ADMIN='-H "X-User-Email: admin@novaplay.vn" -H "X-User-Roles: [ROLE_ADMIN]"'
```

---

## 1. Khởi động và sức khoẻ

| # | Kiểm tra | Lệnh | Kỳ vọng |
|---|---|---|---|
| 1.1 | Service khởi động | log ứng dụng | Không có exception, có dòng `MongoDB index verification completed` |
| 1.2 | Liveness | `curl -s $SVC/actuator/health/liveness` | `"status":"UP"` |
| 1.3 | Readiness gồm cả index | `curl -s $SVC/actuator/health/readiness` | `UP`, và chi tiết có `mongo`, `redis`, `mongoIndex` |
| 1.4 | **Khởi động lại lần hai vẫn được** | Ctrl-C rồi chạy lại | Khởi động thành công. Đây là bài kiểm tra cho lỗi 85 `IndexOptionsConflict` từng làm user-service chết mọi lần boot |
| 1.5 | Index đã tạo đúng | `mongosh movie_service --eval 'db.movies.getIndexes()'` | Có `uk_movie_slug` (unique), `idx_movie_status_release`, `idx_movie_genre`, một text index |
| 1.6 | Đăng ký Eureka | mở `http://localhost:8761` | `MOVIE-SERVICE` ở trạng thái UP |

## 2. Phân quyền

Gọi qua gateway (`$GW`), vì phân quyền là kết quả phối hợp giữa gateway và service.

| # | Kiểm tra | Kỳ vọng |
|---|---|---|
| 2.1 | `GET $GW/api/v1/movies` không token | 200 |
| 2.2 | `GET $GW/api/v1/genres` không token | 200 |
| 2.3 | `POST $GW/api/v1/movies` không token | **401**, không phải 403 |
| 2.4 | `POST $GW/api/v1/movies` với token ROLE_USER | **403** |
| 2.5 | `POST $GW/api/v1/movies` với token ROLE_ADMIN | 201 |
| 2.6 | `GET $GW/api/v1/movies/manage` không token | **401** |
| 2.7 | `GET $GW/api/v1/movies/manage` với token ROLE_ADMIN | 200 — kiểm tra rằng xác thực tuỳ chọn không nuốt mất danh tính của admin |
| 2.8 | Client tự đặt `X-User-Roles: [ROLE_ADMIN]` rồi `POST $GW/api/v1/movies` không token | **401** — gateway phải gỡ header giả mạo |

Khi bật `application.security.gateway-secret.enabled=true`:

| # | Kiểm tra | Kỳ vọng |
|---|---|---|
| 2.9 | `GET $SVC/api/v1/movies` (đi thẳng, bỏ qua gateway) | **403** |
| 2.10 | `GET $GW/api/v1/movies` (qua gateway) | 200 — chứng minh gateway có gắn `X-Gateway-Auth` cho cả traffic ẩn danh |

## 3. Nghiệp vụ

```bash
# Tạo thể loại
curl -s -XPOST $SVC/api/v1/genres -H 'Content-Type: application/json' \
  -H 'X-User-Email: admin@novaplay.vn' -H 'X-User-Roles: [ROLE_ADMIN]' \
  -d '{"name":"Hành động"}'
```

| # | Kiểm tra | Kỳ vọng |
|---|---|---|
| 3.1 | **`createdAt` và `createdBy` khác null** trong document vừa tạo (`mongosh`) | Có giá trị. Null nghĩa là `@Version` bị thiếu và auditing đã bị bỏ qua — đúng lỗi của `FavoriteItem` ở user-service |
| 3.2 | `createdBy` bằng email admin, không phải `"system"` | Chứng minh `AuditorAware` đọc được danh tính từ header |
| 3.3 | Tạo phim không truyền `status` | Document có `status: "DRAFT"`, **không phải null** — bài kiểm tra `@Builder.Default` |
| 3.4 | Tạo trùng tên thể loại | 400, không tạo bản ghi thứ hai |
| 3.5 | Tạo hai phim cùng tiêu đề | Phim thứ hai có slug `<slug>-2` |
| 3.6 | Phim DRAFT không xuất hiện ở `GET /api/v1/movies` | Không có trong danh sách |
| 3.7 | `GET /api/v1/movies/{id}` với phim DRAFT | **404**, không phải 403 |
| 3.8 | Phát hành phim bộ chưa có tập | 400 |
| 3.9 | Gửi tập cho phim lẻ | 400 |
| 3.10 | Gửi hai tập trùng số | 400 |
| 3.11 | Tập được trả về theo thứ tự tăng dần dù gửi lộn xộn | Đúng thứ tự |
| 3.12 | Xoá thể loại đang có phim dùng | 400 |
| 3.13 | Đổi tên thể loại | Tên mới xuất hiện trong chi tiết mọi phim đang dùng thể loại đó |
| 3.14 | Đổi tên nghệ sĩ đóng **hai vai** trong cùng một phim | **Cả hai dòng ê-kíp** đều đổi tên — bài kiểm tra `arrayFilters` |
| 3.15 | `GET /api/v1/movies?size=1000000` | Trả tối đa 100 phần tử |
| 3.16 | `GET /api/v1/movies?sort=description` | 400 |

## 4. Cache

| # | Kiểm tra | Kỳ vọng |
|---|---|---|
| 4.1 | Gọi chi tiết phim hai lần, xem log truy vấn Mongo | Lần thứ hai không sinh truy vấn |
| 4.2 | `redis-cli KEYS 'movie-service:cache:*'` | Có key, và prefix không đụng `user-service:cache:` |
| 4.3 | Admin sửa phim rồi gọi lại chi tiết | Thấy ngay nội dung mới, không phải bản cache cũ |
| 4.4 | Gọi chi tiết một phim không tồn tại hai lần | Cả hai đều 404 và **không** có key null nào trong Redis |

## 5. Vận hành

| # | Kiểm tra | Kỳ vọng |
|---|---|---|
| 5.1 | `curl -s $SVC/actuator/prometheus` không xác thực | 401 — số liệu vận hành không được để lộ |
| 5.2 | Tắt MongoDB rồi gọi readiness | `DOWN`; liveness vẫn `UP` (pod không bị giết oan) |
| 5.3 | Gửi `SIGTERM` khi đang có request chạy | Request hoàn tất rồi tiến trình mới thoát |
| 5.4 | Log có `traceId`/`spanId` khi chạy kèm OTel javaagent | Có giá trị, không phải chuỗi rỗng |
| 5.5 | `docker build -f movie-service/Dockerfile .` | Build thành công, image chạy bằng user không phải root |

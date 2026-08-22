# Chạy NovaPlay trên Kubernetes (dev / docker-desktop) — với Tilt

Trước đây file này là runbook 11 bước gõ tay, và chỉ bao phủ **2/9 service có k8s manifest**
(auth-service + api-gateway). Giờ toàn bộ được điều phối bởi `Tiltfile` ở gốc repo: một lệnh
`tilt up` là khởi động **cả 9 service** (auth, api-gateway, user, movie, media, streaming,
transcoding-worker, notification, config-service) cùng hạ tầng của chúng, kèm web UI xem trạng
thái/log/restart từng service tại `localhost:10350`.

`payment-service`, `report-service` (scaffold rỗng) và `promotion-service` (chưa có k8s manifest)
nằm ngoài phạm vi — vẫn phải chạy/khởi tạo thủ công nếu cần.

> Kiến trúc: người dùng chỉ vào qua **api-gateway** (`localhost:80`). Mọi service khác là
> ClusterIP, không gọi trực tiếp từ ngoài cluster được.

---

## 0. Yêu cầu

- **Docker Desktop** đã bật **Kubernetes** (Settings → Kubernetes → Enable):
  ```bash
  kubectl config current-context     # phải là: docker-desktop
  kubectl get nodes                  # node Ready
  ```
- Đã cài `helm` và [`tilt`](https://docs.tilt.dev/install.html) (`brew install tilt-dev/tap/tilt`
  hoặc script cài trên trang chủ).
- **Dừng docker-compose trước nếu đang chạy** — hai bản triển khai độc lập, trùng cổng
  (5432/6379/9092/27017/8000/8072/...) nếu chạy song song:
  ```bash
  docker compose -f docker-compose/qa/docker-compose.yml down
  ```
- **JWT keypair phải có sẵn** trước khi `tilt up` (Tilt sẽ preflight-check và fail sớm nếu
  thiếu, không tự sinh key vì auth-service/api-gateway phải dùng CÙNG một public key):
  - `auth-service/src/main/resources/keys/private.pem` + `public.pem`
  - `api-gateway/src/main/resources/certs/public.pem` (phải khớp `auth-service/.../public.pem`)

  Nếu chưa có, sinh một cặp key dev mới — private key phải ở dạng PKCS#8, đúng cái
  `AuthServiceKeyConfig` đang parse:

  ```bash
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -out auth-service/src/main/resources/keys/private.pem
  openssl rsa -pubout -in auth-service/src/main/resources/keys/private.pem \
    -out auth-service/src/main/resources/keys/public.pem
  cp auth-service/src/main/resources/keys/public.pem api-gateway/src/main/resources/certs/public.pem
  ```

  Không commit các file `.pem` này (đã gitignore).

  Ba file này KHÔNG còn được bake vào image. `apply-dev-secrets.sh` đọc chúng để tạo Secret
  `auth-service-jwt-keys` và `api-gateway-jwt-public-key`, Pod mount vào `/etc/novaplay/keys`.
  Đây đúng là đường mà prod đi, chỉ khác nguồn sinh Secret (script này ở dev, kubeseal ở prod).
  `auth-service/.dockerignore` và `api-gateway/.dockerignore` chặn `*.pem` khỏi build context để
  private key không bao giờ lọt vào layer image.

- Docker Desktop dùng chung image store với Docker CLI → image `docker_build` xong dùng được
  ngay trong cluster, **không cần push registry**.

---

## 1. Chạy lần đầu

```bash
cp k8s/infra/dev-secrets.env.example k8s/infra/dev-secrets.env
# (tuỳ chọn, chỉ cần nếu dùng cloud config — xem mục 3 bên dưới)
cp tilt-settings.json.example tilt-settings.json

tilt up
```

Giá trị mặc định trong `dev-secrets.env.example` đã khớp với `docker-compose/qa/docker-compose.yml`
và các file `k8s/infra/*-values.yaml` — không cần sửa gì để chạy ở dev.

Mở `http://localhost:10350` để xem Tilt UI: mỗi hạ tầng (postgres/redis/kafka/mongodb/minio/
mailhog), mỗi bước seed, và mỗi service là một resource riêng với trạng thái, log, nút
restart/trigger lại. `tilt up` giữ tiến trình chạy nền và theo dõi file thay đổi; `Ctrl+C` chỉ
thoát CLI, không tắt các resource đã tạo trong cluster (xem mục "Tắt / gỡ" bên dưới).

---

## 2. Tilt tự làm gì

Ánh xạ đúng thứ tự phụ thuộc đã có trong `docker-compose/qa/docker-compose.yml`:

```
postgres / redis / kafka (Bitnami Helm) ─┐
mongodb / media-minio / mailhog (tự viết)─┼─► seed/init (mongo-config-flags-seed,
dev-secrets (9 k8s Secret)                ┘   minio-bucket-init)
                                                        │
        ┌───────────────┬───────────────┬──────────────┼───────────────┬────────────────┐
        ▼               ▼               ▼              ▼               ▼                ▼
  auth-service    config-service   user-service   movie-service  notification-service  media-service
        │               │               │              │               │                │
        └───────────────┴───────────────┴──────────────┴───────┬───────┴────────────────┘
                                                                  ▼
                                                    streaming-service, transcoding-worker
                                                                  │
                                                                  ▼
                                                             api-gateway (localhost:80)
```

Mỗi service (trừ api-gateway) được Tilt port-forward đúng cổng đã biết từ CLAUDE.md/
`application-dev.yml` (8000, 8500, 8600, 8700, 8081, 8200, 8400, 8900). api-gateway **không**
port-forward qua Tilt — Service của nó là `type: LoadBalancer`, Docker Desktop đã tự expose thẳng
ra `localhost:80` (thêm port-forward ở đây sẽ tranh chấp cổng 80 với binding có sẵn đó).

Riêng 4 service sau có **env override chỉ áp dụng khi chạy qua Tilt** (Tiltfile ghi thẳng vào
Deployment lúc apply, KHÔNG sửa file `.yaml` đã commit — các file đó vẫn phản ánh đúng giá trị
prod thật):
- `media-service`, `streaming-service`, `transcoding-worker`: trỏ `STORAGE_PROVIDERS_AWSS3_ENDPOINT`
  + `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` vào MinIO in-cluster thay vì AWS S3 thật.
- `notification-service`: trỏ `MAIL_HOST`/`MAIL_PORT` vào Mailhog in-cluster thay vì
  `smtp.gmail.com`. Xem mail đã gửi tại `localhost:8025`.

---

## 3. Cloud config (Postgres/Redis managed thay vì pod trong cluster)

Trong `tilt-settings.json`:

```json
{
  "use_cloud_postgres": true,
  "cloud_postgres_url": "jdbc:postgresql://<cloud-pg-host>:5432/auth_service?sslmode=require",
  "use_cloud_redis": true,
  "cloud_redis_host": "<cloud-redis-host>",
  "cloud_redis_port": "6379"
}
```

Khi bật, Tilt **bỏ qua** cài Postgres/Redis Bitnami trong cluster và bỏ qua bước tự nạp seed
Postgres. Username/password thật vẫn khai báo trong `k8s/infra/dev-secrets.env`
(`AUTH_SERVICE__DATASOURCE_USERNAME/PASSWORD`, `*_REDIS_PASSWORD`, `API_GATEWAY__REDIS_HOST` v.v.)
— đổi các giá trị đó sang credential cloud thật, **không sửa `configmap.yaml` đã commit**.

**Postgres không cần thao tác thủ công nào nữa.** Liquibase tự dựng schema và nạp hai role
USER/ADMIN lúc auth-service khởi động, kể cả trên một DB cloud hoàn toàn trống. Chỉ cần tạo sẵn
database rỗng (`auth_service`) và cấp quyền tạo bảng cho user trong `DATASOURCE_USERNAME`.

> **Chọn đúng endpoint.** Outbox dùng `LISTEN/NOTIFY`, thứ **không chạy** qua endpoint pooled kiểu
> PgBouncer transaction mode. Supabase: dùng cổng `5432` (direct), không phải `6543` (pooler).
> Neon: dùng host không có hậu tố `-pooler`. Nếu buộc phải đi qua pooler, đặt
> `AUTH_SERVICE__OUTBOX_LISTEN_ENABLED=false` và `AUTH_SERVICE__OUTBOX_POLL_INTERVAL=5s` trong
> `dev-secrets.env` — outbox chuyển sang cơ chế poll, vẫn đúng, chỉ trễ hơn vài giây.
>
> Triệu chứng chọn nhầm endpoint rất khó chẩn đoán: `LISTEN` "thành công" nhưng không notification
> nào tới, nên email OTP chỉ được gửi mỗi khi có một lượt poll/catch-up — không có lỗi nào trong log.

Kafka **luôn** cài in-cluster (không có toggle "cloud Kafka" trong phạm vi hiện tại) — nếu bạn có
Kafka cloud, tự sửa `KAFKA_BOOTSTRAP_SERVERS` tương ứng trong `dev-secrets.env` và bỏ resource
`kafka` trong Tiltfile.

Đảm bảo cloud Postgres/Redis cho phép IP egress của Docker Desktop truy cập (allowlist/firewall).

---

## 4. Monitoring (Prometheus / Grafana / Loki / Tempo / Alloy) — tự động qua Tilt

Đã gộp vào Tiltfile (`use_monitoring`, mặc định `true` trong `tilt-settings.json`). Khi bật,
`tilt up` tự cài trong namespace `monitoring`:

- **kube-prometheus-stack** (Prometheus + Grafana; Alertmanager tắt để đỡ tốn tài nguyên node nhỏ)
- **Loki** (single-binary, filesystem storage) — nhận log
- **Alloy** — DaemonSet đọc log mọi pod qua Kubernetes API, đẩy về Loki
- **Tempo** (single-binary) — nhận trace qua OTLP `:4318`
- **PodMonitor** `novaplay-apps` — cho Prometheus scrape `/actuator/prometheus` của cả 9 service

Values riêng cho từng chart nằm ở `k8s/infra/monitoring/*.yaml`. Grafana đã tự có sẵn 2
datasource Loki + Tempo (cấu hình qua `grafana.additionalDataSources` trong
`kube-prometheus-stack-values.yaml`), cộng datasource Prometheus mặc định của chart.

**Truy cập** (Tilt tự port-forward):
- Grafana: http://localhost:3000 — user `admin`, lấy password:
  ```bash
  kubectl get secret kube-prom-grafana -n monitoring -o jsonpath='{.data.admin-password}' | base64 -d; echo
  ```
- Prometheus: http://localhost:9090
- Loki API: http://localhost:3100 (thường không cần vào trực tiếp, xem qua Grafana → Explore)
- Tempo API: http://localhost:3200 (tương tự, xem qua Grafana → Explore)

**Tắt nếu máy yếu / `tilt up` quá lâu**: đặt `"use_monitoring": false` trong `tilt-settings.json`
rồi chạy lại `tilt up` — Tilt tự gỡ toàn bộ 4 Helm release + namespace `monitoring` vì không còn
khai báo trong Tiltfile nữa.

**Chưa kiểm chứng bằng cluster thật** lúc soạn phần này (môi trường soạn thảo không có
`tilt`/cluster) — rủi ro cao nhất nằm ở `k8s/infra/monitoring/alloy-values.yaml` (key
`alloy.configMap.content` có thể lệch theo version chart `grafana/alloy` cài trên máy bạn). Nếu
resource `alloy` đỏ, chạy `helm show values grafana-charts/alloy | grep -A5 configMap` để đối
chiếu đúng key, hoặc gửi log lỗi lại.

---

## 5. Tắt / gỡ

```bash
tilt down
```

Gỡ sạch mọi resource Tilt đã tạo (bao gồm 3 Helm release postgres/redis/kafka nếu có cài, và
toàn bộ Deployment/Service/ConfigMap/Secret/NetworkPolicy của 9 service + hạ tầng tự viết). **Không**
tắt Docker Desktop hay chính cluster k8s — cluster vẫn sống, chỉ phần NovaPlay bị gỡ.

Kiểm tra đã sạch:
```bash
kubectl get pods,svc,secret,networkpolicy
helm list -A
```

---

## 6. Troubleshooting

| Vấn đề | Chi tiết |
|--------|----------|
| **NetworkPolicy không chặn ở local** | CNI kindnet của docker-desktop **bỏ qua** NetworkPolicy. Manifest sẽ chặn thật trên cluster prod (Calico/Cilium/kube-router của k3s), nhưng ở local pod khác vẫn gọi thẳng được service khác. Hệ quả: sai sót trong NetworkPolicy **không thể phát hiện ở local** — mọi thay đổi phải kiểm chứng trên cluster thật. Các rule hiện tại đã bao phủ đủ 8 cạnh Feign nội bộ (streaming→movie/media/user/config, transcoding-worker→media, user→media, media→config); thêm một lời gọi Feign mới là phải thêm rule tương ứng. |
| **Dữ liệu ephemeral** | Postgres/Mongo để không persistence. Pod bị tạo lại → mất schema/seed/data. Postgres tự phục hồi vì Liquibase chạy lại mỗi lần auth-service khởi động; Mongo cần `mongo-config-flags-seed` chạy lại ở lần `tilt up` kế tiếp — không cần thao tác gì thêm. |
| **`tilt up` lần đầu chậm (1-2 phút/service)** | Dockerfile của các service Maven build cả cây repo (`COPY . .` — pom cha liệt kê toàn bộ module) và tải OTEL agent từ GitHub mỗi lần build, chưa có cache layer tối ưu. Chấp nhận được cho lần đầu; các lần sau Tilt chỉ rebuild service có file thay đổi. |
| **JWT keypair thiếu** | Tilt preflight-check fail ngay khi `tilt up`, xem thông báo lỗi trỏ tới mục 0 ở trên. |
| **Port bị chiếm (5432/6379/9092/27017/...)** | Thường do docker-compose vẫn đang chạy — `docker compose -f docker-compose/qa/docker-compose.yml down` trước. |
| **Secret không nhận giá trị mới sau khi sửa `dev-secrets.env`** | `apply-dev-secrets.sh` chỉ update object Secret, Pod đang chạy KHÔNG tự đọc lại — trigger lại resource `dev-secrets` trong Tilt UI rồi restart resource service tương ứng (nút restart trong UI, tương đương `kubectl rollout restart deployment/<svc>`). |
| **OTel log lỗi kết nối tới Tempo lúc mới `tilt up`** | Bình thường trong vài giây đầu khi resource `tempo` chưa Ready mà service đã start — tự hết khi Tempo lên. Nếu tắt `use_monitoring`, các service vẫn cố gửi tới `tempo.monitoring.svc:4318` (không tồn tại) — vô hại, chỉ log lỗi; muốn tắt hẳn thì set `OTEL_SDK_DISABLED=true` cho service đó (sửa tay, ngoài phạm vi Tiltfile hiện tại). |
| **Resource `alloy`/`kube-prom`/`loki`/`tempo` đỏ hoặc treo** | Khả năng cao do lệch key `values.yaml` so với version chart thật cài trên máy bạn (chưa kiểm chứng bằng cluster thật lúc soạn) — chạy `helm show values <repo>/<chart>` đối chiếu, hoặc gửi log lỗi. Máy yếu: đặt `use_monitoring=false` trong `tilt-settings.json`. |
| **Public key gateway** | Gateway verify JWT bằng public key mount từ Secret `api-gateway-jwt-public-key` (nguồn: `api-gateway/src/main/resources/certs/public.pem`) — phải KHỚP `auth-service/.../keys/public.pem`. Đổi key thì sửa cả hai file, trigger lại resource `dev-secrets`, rồi restart cả `auth-service` lẫn `api-gateway`. KHÔNG cần rebuild image nữa. Triệu chứng lệch key là 401 trên mọi request đã đăng nhập — rất giống token hết hạn nên dễ chẩn đoán nhầm. |

---

## 7. Kiểm tra hệ thống chạy đúng

```bash
# Qua gateway (đúng đường đi thật của app):
curl -X POST http://localhost/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"emailOrUsername":"x","password":"y"}'
# => {"success":false,"message":"Invalid credentials","statusCode":400}  ✓ (chứng minh Postgres
#    schema+seed đã nạp và auth-service/api-gateway wiring đúng)
```

- **Swagger** (qua gateway): http://localhost/swagger-ui.html
- Health từng service qua port-forward của Tilt: `curl localhost:<port>/actuator/health` — xem
  bảng port ở CLAUDE.md.
- **Mailhog**: http://localhost:8025 — kiểm tra email test (OTP, v.v.) có tới không.
- **MinIO console**: http://localhost:9011 (user `media-dev` / pass `media-dev-secret`) — xem
  bucket `novaplay-media` và object media/HLS đã upload.
- **Grafana**: http://localhost:3000 — Explore → chọn datasource Loki xem log tổng hợp mọi
  service, chọn Tempo xem trace theo traceId, dashboard mặc định của kube-prometheus-stack xem
  metrics.

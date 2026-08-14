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
- **JWT keypair phải có sẵn** trước khi build image (Tilt sẽ preflight-check và fail sớm nếu
  thiếu, không tự sinh key vì auth-service/api-gateway phải dùng CÙNG một public key):
  - `auth-service/src/main/resources/keys/private.pem` + `public.pem`
  - `api-gateway/src/main/resources/certs/public.pem` (phải khớp `auth-service/.../public.pem`)

  Nếu chưa có, tự sinh một cặp key dev mới (xem hướng dẫn hiện có của auth-service để sinh RSA
  keypair, rồi copy public key sang api-gateway) — không commit các file `.pem` này (đã
  gitignore).

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
mongodb / media-minio / mailhog (tự viết)─┼─► seed/init (postgres-seed, mongo-config-flags-seed,
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

Việc nạp schema/seed vào Postgres cloud vẫn là thao tác **một lần, thủ công** (chạy trên DB dùng
chung, không an toàn để tự động rerun mỗi `tilt up`):

```bash
export PGPASSWORD='<cloud-pg-password>'
PGCONN="host=<cloud-pg-host> port=5432 dbname=auth_service user=<cloud-pg-user> sslmode=require"
psql "$PGCONN" -f k8s/infra/postgres-initdb.sql
psql "$PGCONN" -f k8s/infra/postgres-seed.sql
```

Kafka **luôn** cài in-cluster (không có toggle "cloud Kafka" trong phạm vi hiện tại) — nếu bạn có
Kafka cloud, tự sửa `KAFKA_BOOTSTRAP_SERVERS` tương ứng trong `dev-secrets.env` và bỏ resource
`kafka` trong Tiltfile.

Đảm bảo cloud Postgres/Redis cho phép IP egress của Docker Desktop truy cập (allowlist/firewall).

---

## 4. Monitoring (Prometheus / Grafana / Loki / Tempo) — vẫn thủ công

Cố tình **không** đưa vào Tiltfile (ngoài phạm vi "9 app service"). Cài bằng Helm như trước:

```bash
kubectl create namespace monitoring
kubectl label namespace monitoring name=monitoring --overwrite

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install kube-prom prometheus-community/kube-prometheus-stack -n monitoring --wait
kubectl port-forward -n monitoring svc/kube-prom-grafana 3000:80   # http://localhost:3000
kubectl get secret kube-prom-grafana -n monitoring -o jsonpath='{.data.admin-password}' | base64 -d; echo
```

Loki/Tempo/Alloy + PodMonitor cho app: xem lịch sử của mục này ở git log của file, hoặc lặp lại
theo pattern trên (`helm install loki grafana/loki -n monitoring ...`,
`helm install tempo grafana/tempo -n monitoring ...`, `helm install alloy grafana/alloy -n
monitoring ...`).

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
| **NetworkPolicy không chặn ở local** | CNI kindnet của docker-desktop **bỏ qua** NetworkPolicy. Manifest đúng và sẽ chặn thật trên cluster prod (Calico/Cilium), nhưng ở local pod khác vẫn gọi thẳng được service khác. |
| **Dữ liệu ephemeral** | Postgres/Mongo để không persistence. Pod bị tạo lại → mất schema/seed/data, `postgres-seed`/`mongo-config-flags-seed` tự chạy lại ở lần `tilt up` kế tiếp — không cần thao tác gì thêm. |
| **`tilt up` lần đầu chậm (1-2 phút/service)** | Dockerfile của các service Maven build cả cây repo (`COPY . .` — pom cha liệt kê toàn bộ module) và tải OTEL agent từ GitHub mỗi lần build, chưa có cache layer tối ưu. Chấp nhận được cho lần đầu; các lần sau Tilt chỉ rebuild service có file thay đổi. |
| **JWT keypair thiếu** | Tilt preflight-check fail ngay khi `tilt up`, xem thông báo lỗi trỏ tới mục 0 ở trên. |
| **Port bị chiếm (5432/6379/9092/27017/...)** | Thường do docker-compose vẫn đang chạy — `docker compose -f docker-compose/qa/docker-compose.yml down` trước. |
| **Secret không nhận giá trị mới sau khi sửa `dev-secrets.env`** | `apply-dev-secrets.sh` chỉ update object Secret, Pod đang chạy KHÔNG tự đọc lại — trigger lại resource `dev-secrets` trong Tilt UI rồi restart resource service tương ứng (nút restart trong UI, tương đương `kubectl rollout restart deployment/<svc>`). |
| **OTel log lỗi kết nối `localhost:4318`/`tempo.monitoring.svc:4318`** | Vô hại nếu chưa deploy monitoring (mục 4) — muốn tắt hẳn, set `OTEL_SDK_DISABLED=true` cho service đó (không phải phạm vi Tiltfile hiện tại, sửa tay nếu cần). |
| **Public key gateway** | Gateway verify JWT bằng `api-gateway/src/main/resources/certs/public.pem` — phải KHỚP `auth-service/.../keys/public.pem`. Đổi key thì đồng bộ cả hai rồi để Tilt tự rebuild image. |

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

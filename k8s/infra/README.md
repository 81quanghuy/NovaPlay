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

Sơ đồ dưới đây là cấu hình MẶC ĐỊNH (mọi thứ in-cluster). Mỗi công tắc ở mục 3 bật lên sẽ xoá
bớt một ô ở hàng trên — với `tilt-settings.json` hiện tại (Supabase + Upstash + Atlas + Aiven +
R2 + Grafana Cloud) hàng đó chỉ còn `mailhog` và `dev-secrets`.

```
postgres / redis / kafka (Bitnami Helm) ─┐
mongodb / media-minio / mailhog (tự viết)─┼─► seed/init (mongo-config-flags-seed,
dev-secrets (10 k8s Secret)               ┘   minio-bucket-init)
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

Tiltfile ghi **env override thẳng vào Deployment lúc apply**, KHÔNG sửa file `.yaml` đã commit.
Cái gì được ghi đè phụ thuộc công tắc ở mục 3:

| Điều kiện | Service | Biến được ghi đè |
|---|---|---|
| luôn luôn | notification-service | `MAIL_HOST`/`MAIL_PORT` → Mailhog in-cluster (xem mail ở `localhost:8025`) |
| `use_cloud_storage: false` | media, streaming, transcoding-worker | `STORAGE_PROVIDERS_AWSS3_ENDPOINT` + `AWS_ACCESS_KEY_ID/SECRET` → MinIO in-cluster |
| `use_cloud_storage: true` | media-service | `DEFAULT_STORAGE_PROVIDER=cloudflare-r2`, `AWS_SQS_ENABLED=false` |
| `use_cloud_kafka: true` | auth, notification, user, media, transcoding-worker | `KAFKA_SECURITY_PROTOCOL=SASL_SSL`, `KAFKA_ADMIN_AUTO_CREATE=false` |
| `use_cloud_kafka: true` | auth, notification, user | `KAFKA_BOOTSTRAP_SERVERS` → URI Aiven (media/transcoding đọc từ Secret) |
| `use_cloud_postgres/redis: true` | tương ứng | `DATASOURCE_URL`, `REDIS_HOST/PORT/SSL_ENABLED` |
| `use_monitoring: true` | 7 service (auth/api-gateway đã hardcode sẵn) | `OTEL_EXPORTER_OTLP_ENDPOINT` → `alloy.monitoring.svc:4318` |
| `dev_scale_down: true` (mặc định) | cả 9 service | `replicas: 1` + trần `requests` 250m/512Mi — chỉ requests, **không** đụng limits |

---

## 3. Cloud config — thứ gì chạy ngoài cụm, bật bằng công tắc nào

Toàn bộ nằm ở `tilt-settings.json` (không commit). Mỗi công tắc bật lên là Tilt **bỏ hẳn** phần
hạ tầng tương ứng trong cụm:

| Công tắc | Bật = bỏ khỏi cụm | Credential nằm ở |
|---|---|---|
| `use_cloud_postgres` + `cloud_postgres_url` | Helm release `postgres` | `AUTH_SERVICE__DATASOURCE_USERNAME/PASSWORD` |
| `use_cloud_redis` + `cloud_redis_host` | Helm release `redis` | `*__REDIS_PASSWORD`, `API_GATEWAY__REDIS_HOST`, `STREAMING_SERVICE__REDIS_HOST` |
| `use_cloud_mongo` | Deployment `mongodb` + seed | 6 dòng `*__MONGODB_URI` |
| `use_cloud_kafka` + `cloud_kafka_bootstrap` | Helm release `kafka` | `*__KAFKA_SASL_USERNAME/PASSWORD` + file CA |
| `use_cloud_storage` | Deployment `media-minio` + `minio-bucket-init` | `*__R2_*` |
| `use_grafana_cloud` | kube-prometheus-stack + Loki + Tempo | `GRAFANA_CLOUD__*` |

Giá trị thật điền ở `k8s/infra/dev-secrets.env` (cũng không commit) — **không** sửa
`configmap.yaml`/`deployment.yaml` đã commit, Tiltfile tự ghi đè lúc apply.

Đảm bảo mọi nhà cung cấp cho phép IP egress của máy bạn truy cập (allowlist/firewall).

### 3.1 Postgres (Supabase / Neon)

**Không cần thao tác thủ công nào.** Liquibase tự dựng schema và nạp hai role USER/ADMIN lúc
auth-service khởi động, kể cả trên DB hoàn toàn trống. Chỉ cần tạo sẵn database rỗng và cấp
quyền tạo bảng cho user trong `DATASOURCE_USERNAME`.

> **Chọn đúng endpoint.** Outbox dùng `LISTEN/NOTIFY`, thứ **không chạy** qua endpoint pooled kiểu
> PgBouncer transaction mode. Supabase: dùng cổng `5432` (direct/session), không phải `6543`
> (transaction pooler). Neon: host không có hậu tố `-pooler`. Nếu buộc phải đi qua pooler, đặt
> `AUTH_SERVICE__OUTBOX_LISTEN_ENABLED=false` và `AUTH_SERVICE__OUTBOX_POLL_INTERVAL=5s` trong
> `dev-secrets.env` — outbox chuyển sang cơ chế poll, vẫn đúng, chỉ trễ vài giây.
>
> Triệu chứng chọn nhầm rất khó chẩn đoán: `LISTEN` "thành công" nhưng không notification nào tới,
> nên email OTP chỉ được gửi mỗi khi có một lượt poll/catch-up — không có lỗi nào trong log.

### 3.2 Redis (Upstash / Redis Cloud)

`cloud_redis_host` chỉ nhận **hostname thuần**, không phải URI `redis://user:pass@host:port`.
`cloud_redis_ssl_enabled: true` cho hầu hết Redis cloud.

> **Free tier Upstash = 500.000 lệnh/tháng (~16.6k/ngày), 256MB, 10GB băng thông.** Mỗi request
> qua gateway đã ăn 2-3 lệnh cho rate limiter, cộng check JWT blacklist, cộng cache
> movie/manifest/entitlement, cộng dedup của notification. Một buổi load test là hết quota tháng
> và **mọi service dùng Redis đồng loạt lỗi**, không riêng cái đang test.

### 3.3 Kafka managed — Aiven (`use_cloud_kafka`)

Aiven Console → service Kafka → **Connect information** → tab *Apache Kafka*, Authentication
method = **SASL** (không phải Client certificate — hai cách dùng hai **cổng khác nhau**, dán nhầm
port thì lỗi là connection timeout chứ không phải sai mật khẩu).

Ba thứ phải lấy từ màn hình đó:

1. **Service URI** (`<service>-<project>.<region>.aivencloud.com:10550`) → `cloud_kafka_bootstrap`
   trong `tilt-settings.json`, **và** hai dòng `MEDIA_SERVICE__KAFKA_BOOTSTRAP_SERVERS` /
   `TRANSCODING_WORKER__KAFKA_BOOTSTRAP_SERVERS` trong `dev-secrets.env` (hai service này đọc
   bootstrap từ Secret chứ không phải ConfigMap).
2. **User / Password** (`avnadmin`) → `<SERVICE>__KAFKA_SASL_USERNAME/PASSWORD` cho cả 5 service
   dùng Kafka: auth, notification, user, media, transcoding-worker.
3. **CA certificate** → Download, lưu thành `k8s/infra/aiven-kafka-ca.pem` (đã gitignore qua
   `*.pem`). `apply-dev-secrets.sh` biến nó thành Secret dùng chung `aiven-kafka-ca`, cả 5
   Deployment mount vào `/etc/novaplay/kafka/ca.pem`.

   **Đây là bước hay bị bỏ sót nhất.** Broker Aiven do CA riêng của project ký, JVM không tin
   sẵn: thiếu CA thì lỗi là `SSLHandshakeException ... unable to find valid certification path`
   ngay lúc bắt tay TLS — rất dễ đi debug nhầm sang username/password. File PEM được Kafka client
   nạp thẳng (`ssl.truststore.type=PEM`), **không** cần dựng JKS bằng `keytool`.

Kết nối do 4 biến điều khiển, mặc định trong `application-prod.yml` vẫn là PLAINTEXT nên Kafka
in-cluster/docker-compose/Testcontainers không đổi gì: `KAFKA_SECURITY_PROTOCOL=SASL_SSL`
(Tiltfile tự đặt), `KAFKA_SASL_MECHANISM=SCRAM-SHA-256`, `KAFKA_SASL_USERNAME`,
`KAFKA_SASL_PASSWORD`.

#### Topic — tự tạo lúc khởi động (plan đã nâng, không còn quota 5 topic)

`KAFKA_ADMIN_AUTO_CREATE=true`: mỗi service khi khởi động gọi `AdminClient.createTopics` cho các
`NewTopic` bean của nó. Idempotent — topic đã tồn tại thì `TopicExistsException` bị nuốt, không
sao cả. Không phải tạo tay gì trong Aiven Console.

Lưu ý đây là **createTopics tường minh**, khác hẳn broker setting `auto_create_topics_enable` của
Aiven (vẫn `false`) vốn chỉ chi phối việc tự sinh topic khi produce vào một topic lạ. Nghĩa là:
topic nào **không** có `NewTopic` bean thì vẫn không tồn tại.

11 topic được tạo:

| Topic | Producer → Consumer | Bean khai báo ở |
|---|---|---|
| `send-email.v1` (+`.DLT`) | auth-service (outbox) → notification-service | auth, notification |
| `activate-account.v1` (+`.DLT`) | auth-service (outbox) → notification-service, user-service | auth, notification, user |
| `notification.requested.v1` (+`.DLT`) | (chưa có producer) → notification-service | notification |
| `send-status-media.v1` (+`.DLT`) | media-service → user-service | media, user |
| `video-source-ready.v1` (+`.DLT`) | media-service → transcoding-worker | media, transcoding-worker |
| `video-transcode-completed.v1` | media-service → (chưa có consumer) | media |

Ba topic của promotion-service (`create-referral.v1`, `qualify-referral.v1`, `redeem-coupon.v1`)
KHÔNG được tạo: service đó chưa có k8s manifest nên không chạy trong cụm.

> **Kiểm tra lần khởi động đầu tiên.** Các `NewTopic` bean đang hardcode `.replicas(1)`, con số
> hợp lý cho Kafka in-cluster 1 broker nhưng chưa chắc hợp lệ trên plan Aiven nhiều broker. Hai
> lỗi cần soi trong log:
>
> ```bash
> tilt logs auth-service | grep -iE "createTopics|InvalidReplicationFactor|PolicyViolation|NOT_ENOUGH_REPLICAS"
> ```
>
> - `InvalidReplicationFactorException` / `PolicyViolationException` → Aiven từ chối RF=1. Tạo
>   topic bằng Aiven Console (để RF mặc định của plan), rồi đặt lại
>   `KAFKA_ADMIN_AUTO_CREATE=false` trong Tiltfile.
> - `NOT_ENOUGH_REPLICAS` lúc produce → topic đã tạo với RF=1 nhưng broker đặt
>   `min.insync.replicas=2`, mà auth-service produce với `acks=all`. Sửa RF của topic trong
>   Console lên bằng `min.insync.replicas`.
>
> Không thấy hai lỗi đó nghĩa là mọi thứ ổn, không cần làm gì thêm.

### 3.4 Object storage — Cloudflare R2 (`use_cloud_storage`)

1. Tạo bucket trong Cloudflare R2 (ví dụ `novaplay-media`).
2. R2 → **Manage API tokens** → Create API token, quyền *Object Read & Write* cho bucket đó. Lấy
   Access Key ID + Secret Access Key.
3. Điền `R2_ACCOUNT_ID` / `R2_BUCKET_NAME` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` cho **cả
   ba** service `MEDIA_SERVICE__`, `STREAMING_SERVICE__`, `TRANSCODING_WORKER__` trong
   `dev-secrets.env`. Endpoint được ghép tự động thành
   `https://<R2_ACCOUNT_ID>.r2.cloudflarestorage.com`.

   Ba service **phải cùng bucket**: media ký URL upload, worker ghi HLS output, streaming đọc
   segment ra. Lệch bucket là playback trả 404 dù transcode báo thành công — và không có log lỗi
   nào ở giữa để lần ra.

4. `MEDIA_SERVICE__R2_CDN_BASE_URL` để **trống** nếu chưa gắn custom domain/`r2.dev` public cho
   bucket. Trống = dùng presigned URL, luôn đúng. Điền sai = URL trả về cho client 404 mà
   media-service không hề biết.

**Cờ trong Mongo thắng biến môi trường.** Tiltfile đặt `DEFAULT_STORAGE_PROVIDER=cloudflare-r2`
cho media-service, nhưng đó chỉ là *default* của cờ OpenFeature `media-storage-provider`. Nếu
collection `config_flags` của config-service đã có document đó (ví dụ từ lần seed trước với
`aws-s3`), **cờ thắng** và upload mới vẫn đi vào S3. Trên Atlas, sửa bằng:

```bash
mongosh "<CONFIG_SERVICE__MONGODB_URI>" --eval '
  db.config_flags.updateOne(
    { _id: "media-storage-provider" },
    { $set: { value: "cloudflare-r2" } },
    { upsert: true })'
```

Cờ này chỉ áp dụng cho **upload mới**. Media đã tồn tại vẫn dùng provider đã ghi trong record
(`getEffectiveStorageProvider`) — cố ý như vậy, nếu không thì xoá/kiểm tra object sẽ nhìn nhầm
bucket. Nghĩa là video upload thời MinIO sẽ không phát được nữa sau khi chuyển sang R2: chúng trỏ
tới một MinIO không còn tồn tại.

Tiltfile cũng đặt `AWS_SQS_ENABLED=false` cho media-service: R2 không phát S3 event nào vào SQS,
để `true` là giữ một consumer AWS thật vô nghĩa (và là chỗ duy nhất còn cần credential AWS).

> **Đừng nâng AWS SDK v2 lên ≥ 2.30 mà không kiểm tra lại R2.** Cả ba service đang ghim
> `software.amazon.awssdk:bom:2.25.43`. Từ 2.30, SDK mặc định gắn checksum CRC32 dạng trailer vào
> mọi PutObject, thứ R2 từ chối — biểu hiện là upload lỗi `XAmzContentSHA256Mismatch` / header
> not implemented. Nếu buộc phải nâng, đặt `AWS_REQUEST_CHECKSUM_CALCULATION=when_required`.

---

## 4. Monitoring — hai chế độ

`use_monitoring` bật/tắt toàn bộ. Khi bật, `use_grafana_cloud` quyết định telemetry đi đâu.

Ở **cả hai** chế độ, Alloy là điểm nhận OTLP duy nhất: mọi service đặt
`OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy.monitoring.svc:4318`, Alloy forward tiếp. Trước đây
biến này trỏ thẳng `tempo.monitoring.svc` nên chuyển sang cloud là mất trace mà không có lỗi nào.

### 4.1 `use_grafana_cloud: true` — nhẹ nhất cho máy local

Chỉ **một** pod monitoring chạy trong cụm (Alloy, Deployment 1 replica), làm cả ba việc:

```
pod /actuator/prometheus  --scrape 30s-->  remote_write  -->  Grafana Cloud Prometheus
stdout của pod            --tail-------->  loki.write    -->  Grafana Cloud Loki
OTLP :4318 (OTEL agent)   --receive----->  otlphttp      -->  Grafana Cloud Tempo
```

Không còn Prometheus, Grafana, Loki, Tempo trong cụm → tiết kiệm ~2-3GB RAM. Xem dashboard trên
grafana.net, không phải `localhost:3000`.

Credential lấy ở grafana.com → stack của bạn → Details của từng mục, điền vào 7 key
`GRAFANA_CLOUD__*` trong `dev-secrets.env`. `apply-dev-secrets.sh` tạo Secret
`grafana-cloud-secrets` trong namespace `monitoring` (tự tạo namespace kèm label
`name=monitoring` — label này bắt buộc, NetworkPolicy của 9 service chỉ cho scrape từ namespace
có nó). TOKEN là **một** Access Policy Token dùng chung cho cả ba, scope `metrics:write`,
`logs:write`, `traces:write`.

> **Free tier = 10.000 active series.** `alloy-cloud-values.yaml` cố ý **drop**
> `http_server_requests_seconds_bucket` và hai histogram tương tự trong
> `prometheus.relabel "cardinality_guard"`: riêng histogram của Micrometer sinh ~1 series cho mỗi
> tổ hợp (uri × method × status × bucket), 9 service là đủ chạm trần một mình. Đánh đổi: còn
> throughput/tỉ lệ lỗi/latency trung bình, mất percentile và heatmap. Bỏ rule đó thì nhớ theo dõi
> mục Usage — vượt 10k series là Grafana Cloud bắt đầu **từ chối ghi**.
>
> Vì cùng lý do đó Alloy chạy Deployment 1 replica chứ không DaemonSet: DaemonSet nghĩa là mỗi
> node scrape toàn bộ pod, series nhân lên theo số node.

### 4.2 `use_grafana_cloud: false` — toàn bộ in-cluster (như cũ)

`tilt up` cài trong namespace `monitoring`: **kube-prometheus-stack** (Prometheus + Grafana,
Alertmanager tắt), **Loki** (single-binary, filesystem), **Tempo** (single-binary), **Alloy**
(DaemonSet: log qua Kubernetes API → Loki, OTLP → Tempo), và **PodMonitor** `novaplay-apps` cho
Prometheus scrape `/actuator/prometheus` của cả 9 service.

Truy cập (Tilt tự port-forward): Grafana http://localhost:3000 (user `admin`), Prometheus
http://localhost:9090, Loki http://localhost:3100, Tempo http://localhost:3200.

```bash
kubectl get secret kube-prom-grafana -n monitoring -o jsonpath='{.data.admin-password}' | base64 -d; echo
```

### 4.3 Mức độ đã kiểm chứng

- Nội dung `alloy.configMap.content` của **cả hai** values file đã pass `alloy fmt` và
  `alloy validate` (Alloy v1.18) — component, tham số và tham chiếu đều resolve được.
- Các key values (`alloy.configMap.create/content`, `alloy.envFrom`, `alloy.extraPorts`,
  `alloy.resources`, `controller.type/replicas`) đã đối chiếu với chart `grafana-charts/alloy`
  **1.11.1**. Chart cũ/mới hơn có thể đổi key — resource `alloy` đỏ thì chạy
  `helm show values grafana-charts/alloy` để so lại.
- **Chưa** chạy thử end-to-end với credential Grafana Cloud thật: đường từ Alloy ra ngoài
  (URL/token đúng chưa, có bị 401 không) chỉ biết được khi `tilt up`.

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
| **`Build Failed: apply command timed out after 30s`** | Timeout mặc định của Tilt, không phải chart hỏng: `helm --wait` chờ pod Ready nên luôn quá 30s. Tiltfile đã đặt `update_settings(k8s_upsert_timeout_secs=600)` — nếu vẫn gặp thì Tiltfile chưa reload, `tilt down && tilt up`. |
| **Pod đứng `Pending` / `Insufficient memory`** | `dev_scale_down` đang false, hoặc VM Docker Desktop quá nhỏ. Với `dev_scale_down: true` tổng requests là 2.1 vCPU / 4.2 GiB (từ 5.7 vCPU / 11.5 GiB). |
| **Port bị chiếm (5432/6379/9092/27017/...)** | Thường do docker-compose vẫn đang chạy — `docker compose -f docker-compose/qa/docker-compose.yml down` trước. |
| **Secret không nhận giá trị mới sau khi sửa `dev-secrets.env`** | `apply-dev-secrets.sh` chỉ update object Secret, Pod đang chạy KHÔNG tự đọc lại — trigger lại resource `dev-secrets` trong Tilt UI rồi restart resource service tương ứng (nút restart trong UI, tương đương `kubectl rollout restart deployment/<svc>`). |
| **OTel log lỗi kết nối tới Alloy lúc mới `tilt up`** | Bình thường trong vài giây đầu khi resource `alloy` chưa Ready mà service đã start — tự hết khi Alloy lên. Nếu tắt `use_monitoring`, các service vẫn cố gửi tới `alloy.monitoring.svc:4318` (không tồn tại) — vô hại, chỉ log lỗi; muốn tắt hẳn thì set `OTEL_SDK_DISABLED=true` cho service đó. |
| **`SSLHandshakeException: unable to find valid certification path` lúc service kết nối Kafka** | Thiếu CA của Aiven. Kiểm tra `k8s/infra/aiven-kafka-ca.pem` có tồn tại không, rồi trigger lại resource `dev-secrets` và restart service. `kubectl get secret aiven-kafka-ca` phải tồn tại. KHÔNG phải lỗi sai username/password. |
| **Kafka `TimeoutException: Topic ... not present in metadata` khi produce** | KafkaAdmin không tạo được topic. Grep log theo mục 3.3 — thường là Aiven từ chối `replicas(1)`, tạo tay trong Console là xong. |
| **Upload lên R2 báo 403/SignatureDoesNotMatch** | Sai `R2_ACCOUNT_ID` (endpoint sai host) hoặc API token không có quyền Object Read & Write đúng bucket. Ba service phải cùng account + cùng bucket. |
| **Video cũ không phát được sau khi chuyển sang R2** | Đúng như thiết kế: record Media giữ provider đã ghi lúc upload (`getEffectiveStorageProvider`), video upload thời MinIO vẫn trỏ MinIO — nay không còn. Upload lại. |
| **Grafana Cloud không thấy metric nào** | Ba khả năng: (1) namespace `monitoring` thiếu label `name=monitoring` → NetworkPolicy chặn scrape; (2) sai token/user (Alloy log 401); (3) vượt 10k active series → Grafana Cloud từ chối ghi, xem mục Usage. |
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

# Chạy NovaPlay trên Kubernetes (dev / docker-desktop) — Hướng dẫn từng bước

Hướng dẫn dựng **auth-service + api-gateway + hạ tầng (PostgreSQL/Redis/Kafka)** lên
cluster Kubernetes của Docker Desktop, và cách **tắt/gỡ** toàn bộ. Có kèm Headlamp (web
dashboard) ở cuối.

> Kiến trúc: người dùng chỉ vào qua **api-gateway** (`localhost:80`). auth-service để
> ClusterIP + NetworkPolicy nên không gọi trực tiếp từ ngoài được.
>
> ```
> Trình duyệt → api-gateway :80 → auth-service :8000 → postgres/redis/kafka
> ```

---

## 0. Yêu cầu

- **Docker Desktop** đã bật **Kubernetes** (Settings → Kubernetes → Enable). Kiểm tra:
  ```bash
  kubectl config current-context     # phải là: docker-desktop
  kubectl get nodes                  # node Ready
  ```
- `helm` đã cài.
- Lệnh bên dưới chạy từ **thư mục gốc repo** (`NovaPlay/`), trừ khi ghi rõ `cd k8s/infra`.

> Docker Desktop k8s dùng chung image store với Docker → image build ở máy dùng được ngay
> trong cluster, **không cần push registry**.

---

## 1. Build image cho 2 service

```bash
docker build -t novaplay/authservice:v0.0.2 auth-service/
docker build -t novaplay/apigateway:v1.0.1  api-gateway/
```

> ⚠️ **Đổi code là phải bump tag** (vd `:v0.0.3`) rồi sửa tag trong
> `k8s/<svc>/deployment.yaml`. Docker Desktop cache image theo tag — build lại **cùng tag**
> thì node vẫn chạy bản cũ.

---

## 2. Cài hạ tầng bằng Helm (Bitnami)

> Đã có **Postgres/Redis trên cloud**? Xem **Mục 11** — bỏ cài postgres/redis ở đây, chỉ cài kafka.

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update bitnami

cd k8s/infra
helm install postgres bitnami/postgresql -f postgresql-values.yaml --wait
helm install redis    bitnami/redis      -f redis-values.yaml      --wait
helm install kafka    bitnami/kafka      -f kafka-values.yaml      --wait
cd ../..
```

Kết quả — các Service: `postgres:5432`, `redis-master:6379`, `kafka:9092`.

> Bitnami free-tier đã xóa tag phiên bản khỏi `docker.io/bitnami/*`:
> - postgres/redis: chart default `tag: latest` → vẫn pull được.
> - kafka: `kafka-values.yaml` trỏ image sang `bitnamilegacy` + `allowInsecureImages=true`.

---

## 3. Nạp schema + seed vào PostgreSQL

Prod dùng `ddl-auto: validate` (KHÔNG tự tạo bảng) và có health check `dbSeed` (cần role
`USER`). Vì `persistence.enabled=false`, **phải nạp lại mỗi khi pod postgres tạo mới**:

```bash
cd k8s/infra
# Schema (7 bảng, gồm outbox_events)
cat postgres-initdb.sql | kubectl exec -i postgres-0 -- env PGPASSWORD=admin123 psql -U admin -d auth_service
# Seed roles (USER, ADMIN) — thiếu thì /actuator/health = DOWN
cat postgres-seed.sql   | kubectl exec -i postgres-0 -- env PGPASSWORD=admin123 psql -U admin -d auth_service
cd ../..
```

> File dump lấy từ Postgres của docker-compose (`pg_dump`). Khi schema đổi thì dump lại.

---

## 4. Tạo Secrets (KHÔNG commit giá trị thật)

```bash
# auth-service: credential Postgres + Redis
kubectl create secret generic auth-service-secrets \
  --from-literal=DATASOURCE_USERNAME='admin' \
  --from-literal=DATASOURCE_PASSWORD='admin123' \
  --from-literal=REDIS_PASSWORD='redis123'

# api-gateway: Redis (REDIS_SSL_ENABLED=false vì redis-master không SSL) + shared secret
kubectl create secret generic api-gateway-secrets \
  --from-literal=GATEWAY_SHARED_SECRET='novaplay-gateway-shared-secret' \
  --from-literal=REDIS_HOST='redis-master' \
  --from-literal=REDIS_PORT='6379' \
  --from-literal=REDIS_PASSWORD='redis123' \
  --from-literal=REDIS_SSL_ENABLED='false'
```

---

## 5. Deploy auth-service

```bash
kubectl apply -f k8s/auth-service/configmap.yaml \
              -f k8s/auth-service/service.yaml \
              -f k8s/auth-service/deployment.yaml \
              -f k8s/auth-service/networkpolicy.yaml
kubectl rollout status deployment/auth-service
```

---

## 6. Deploy api-gateway (điểm vào :80)

```bash
kubectl apply -f k8s/api-gateway/configmap.yaml \
              -f k8s/api-gateway/service.yaml \
              -f k8s/api-gateway/deployment.yaml \
              -f k8s/api-gateway/networkpolicy.yaml
kubectl rollout status deployment/api-gateway
```

---

## 7. Kiểm tra

```bash
kubectl get pods        # tất cả 1/1 Running
kubectl get svc         # api-gateway là LoadBalancer, port 80

# Gọi auth-service QUA gateway (đúng đường của app):
curl -X POST http://localhost/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"emailOrUsername":"x","password":"y"}'
# => {"success":false,"message":"Invalid credentials","statusCode":400}  ✓
```

- **Swagger** (qua gateway): http://localhost/swagger-ui.html
- Chỉ cần nhớ **một cổng: `localhost:80`**. Các service khác là ClusterIP (nội bộ cluster).

> Muốn gọi thẳng auth-service để debug (bỏ qua gateway): `kubectl port-forward
> svc/auth-service 18000:8000` rồi vào `localhost:18000`. Ống tạm, tắt lệnh là mất.

---

## 8. Headlamp — web dashboard (tùy chọn)

```bash
helm repo add headlamp https://kubernetes-sigs.github.io/headlamp/
helm repo update headlamp
helm install headlamp headlamp/headlamp -n kube-system --wait

# Mở dashboard: http://localhost:8090
kubectl port-forward -n kube-system svc/headlamp 8090:80
```

Đăng nhập: chart tạo sẵn SA `headlamp` (đã có cluster-admin). Tạo token, dán nguyên chuỗi
`eyJ...` vào ô token → Authenticate:

```bash
kubectl create token headlamp -n kube-system --duration=168h
```

> Token phải của SA **`headlamp`** (không phải SA khác), nếu không sẽ báo
> `namespaces is forbidden`. Trong UI chọn Namespace = `default` để thấy app.

---

## 9. Tắt / gỡ toàn bộ

### 9a. Tắt các port-forward tạm (Headlamp, debug)
Nhấn `Ctrl+C` ở cửa sổ đang chạy `kubectl port-forward`, hoặc:
```bash
pkill -f "kubectl port-forward"
```

### 9b. Gỡ ứng dụng (auth-service + api-gateway)
```bash
kubectl delete -f k8s/api-gateway/configmap.yaml \
               -f k8s/api-gateway/service.yaml \
               -f k8s/api-gateway/deployment.yaml \
               -f k8s/api-gateway/networkpolicy.yaml
kubectl delete -f k8s/auth-service/configmap.yaml \
               -f k8s/auth-service/service.yaml \
               -f k8s/auth-service/deployment.yaml \
               -f k8s/auth-service/networkpolicy.yaml
kubectl delete secret auth-service-secrets api-gateway-secrets
```

### 9c. Gỡ hạ tầng
```bash
helm uninstall kafka redis postgres
```

### 9d. Gỡ Headlamp
```bash
helm uninstall headlamp -n kube-system   # xoá luôn SA + binding do chart tạo
```

### 9e. (Tùy chọn) Kiểm tra đã sạch
```bash
kubectl get pods,svc,secret,networkpolicy
helm list -A
```

> **Tắt app ≠ tắt Docker Desktop.** Cluster k8s chạy bên trong Docker Desktop; tắt Docker
> Desktop là cả cluster chết. Chỉ cần các lệnh trên là gỡ sạch phần NovaPlay, cluster vẫn sống.

---

## 10. Monitoring (Prometheus / Grafana / Loki / Tempo) trên k8s

Stack observability trong docker-compose ánh xạ sang Helm chart:

| docker-compose | k8s (Helm chart) |
|----------------|------------------|
| prometheus + grafana | `prometheus-community/kube-prometheus-stack` (kèm Alertmanager, node-exporter, kube-state-metrics) |
| loki-write/read/backend/gateway + minio | `grafana/loki` (dev: single-binary, lưu filesystem — **không cần MinIO**) |
| tempo | `grafana/tempo` (single binary, nhận OTLP 4318) |
| alloy | `grafana/alloy` (thu log pod → Loki) |

> Đây là bản **rút gọn cho dev**: Loki chạy single-binary + filesystem thay vì kiểu
> microservice + MinIO như compose. Đủ để xem metrics/logs/traces ở local.

### 10.1 Tạo namespace `monitoring` (phải có label)
```bash
kubectl create namespace monitoring
# NetworkPolicy của auth-service/api-gateway cho phép scrape từ ns có label name=monitoring
kubectl label namespace monitoring name=monitoring --overwrite
```

### 10.2 Prometheus + Grafana
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install kube-prom prometheus-community/kube-prometheus-stack -n monitoring --wait
```
Mở Grafana (đã kèm sẵn datasource Prometheus):
```bash
kubectl port-forward -n monitoring svc/kube-prom-grafana 3000:80   # http://localhost:3000
# user: admin ; password:
kubectl get secret kube-prom-grafana -n monitoring -o jsonpath='{.data.admin-password}' | base64 -d; echo
```

### 10.3 Loki (logs)
```bash
cat > /tmp/loki-values.yaml <<'EOF'
deploymentMode: SingleBinary
loki:
  auth_enabled: false
  commonConfig: { replication_factor: 1 }
  storage: { type: filesystem }
  schemaConfig:
    configs:
      - from: "2024-01-01"
        store: tsdb
        object_store: filesystem
        schema: v13
        index: { prefix: index_, period: 24h }
  limits_config: { allow_structured_metadata: true }
singleBinary: { replicas: 1 }
read: { replicas: 0 }
write: { replicas: 0 }
backend: { replicas: 0 }
chunksCache: { enabled: false }
resultsCache: { enabled: false }
lokiCanary: { enabled: false }
gateway: { enabled: false }
test: { enabled: false }
EOF
helm install loki grafana/loki -n monitoring -f /tmp/loki-values.yaml --wait
```

### 10.4 Alloy (thu log pod → Loki)
```bash
helm install alloy grafana/alloy -n monitoring --wait
```
Cấu hình Alloy đọc log container và đẩy về `http://loki.monitoring.svc:3100/loki/api/v1/push`
(xem docs chart `grafana/alloy`), hoặc dùng `grafana/promtail` nếu quen hơn.

### 10.5 Tempo (traces) + đấu OTEL từ app
```bash
helm install tempo grafana/tempo -n monitoring --wait   # nhận OTLP ở cổng 4318
```
Trỏ app gửi trace về Tempo — **đồng thời hết log lỗi `localhost:4318`** trong pod.
Tempo CHỈ nhận traces; phải tắt OTLP metrics + logs (giống `common-config.yml` của compose),
nếu không agent bắn metrics/logs vào Tempo và bị **404**:
```bash
kubectl set env deployment/auth-service deployment/api-gateway \
  OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo.monitoring.svc:4318 \
  OTEL_METRICS_EXPORTER=none \
  OTEL_LOGS_EXPORTER=none
```
> - Metrics đã do Prometheus scrape (`/actuator/prometheus`), logs do Alloy→Loki — không đẩy qua OTLP.
> - Muốn cố định thì thêm 3 biến này vào `k8s/<svc>/deployment.yaml` thay vì set tay.
> - Kiểm tra Tempo đã nhận trace: `kubectl port-forward -n monitoring svc/tempo 3200:3200`
>   rồi `curl 'http://localhost:3200/metrics' | grep spans_received_total`.

### 10.6 Thêm datasource Loki/Tempo vào Grafana
Prometheus đã có sẵn. Thêm 2 datasource trong Grafana → Connections → Data sources:
- **Loki**: `http://loki.monitoring.svc:3100`
- **Tempo**: `http://tempo.monitoring.svc:3100`

### 10.7 Cho Prometheus scrape app (PodMonitor)
kube-prometheus-stack scrape theo **PodMonitor/ServiceMonitor**, KHÔNG theo annotation
`prometheus.io/scrape`. Khai báo PodMonitor cho 2 app:
```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: PodMonitor
metadata:
  name: novaplay-apps
  namespace: monitoring
  labels: { release: kube-prom }      # để Prometheus của kube-prom nhặt
spec:
  namespaceSelector: { matchNames: [default] }
  selector:
    matchExpressions:
      - { key: app, operator: In, values: [auth-service, api-gateway] }
  podMetricsEndpoints:
    - port: http
      path: /actuator/prometheus
EOF
```
> Container đã có port tên `http` và `/actuator/prometheus` đã được permit ở auth-service.
> Nếu api-gateway trả 401/403 tại path này thì mở thêm trong security config của gateway.

### 10.8 Gỡ monitoring
```bash
kubectl delete podmonitor novaplay-apps -n monitoring
# gỡ biến OTEL đã set (dấu '-' ở cuối = xoá biến)
kubectl set env deployment/auth-service deployment/api-gateway OTEL_EXPORTER_OTLP_ENDPOINT-
helm uninstall alloy tempo loki kube-prom -n monitoring
kubectl delete namespace monitoring
```

---

## 11. Dùng PostgreSQL & Redis Cloud (thay cho pod trong cluster)

Nếu đã có **Postgres/Redis managed trên cloud**, KHÔNG cài Bitnami postgres/redis nữa —
chỉ cần trỏ config sang endpoint cloud. (Kafka thì vẫn cài trong cluster ở Bước 2, trừ khi
bạn cũng có Kafka cloud.)

### 11.1 Chỉ cài Kafka ở Bước 2
```bash
cd k8s/infra
helm install kafka bitnami/kafka -f kafka-values.yaml --wait
cd ../..
# Nếu lỡ cài rồi thì gỡ postgres/redis in-cluster:
# helm uninstall postgres redis
```
→ **Bỏ qua Bước 3** (schema nạp thẳng vào Postgres cloud ở 11.2 dưới đây).

### 11.2 Nạp schema + seed vào Postgres cloud (chạy 1 lần, từ máy có `psql`)
Prod dùng `ddl-auto: validate` nên bảng phải có sẵn:
```bash
export PGPASSWORD='<cloud-pg-password>'
PGCONN="host=<cloud-pg-host> port=5432 dbname=auth_service user=<cloud-pg-user> sslmode=require"
psql "$PGCONN" -f k8s/infra/postgres-initdb.sql   # 7 bảng
psql "$PGCONN" -f k8s/infra/postgres-seed.sql     # roles USER/ADMIN
```
> Đảm bảo DB `auth_service` đã tồn tại trên cloud (tạo trước bằng `CREATE DATABASE auth_service;`
> nếu cloud chưa có).

### 11.3 Trỏ auth-service sang cloud
Sửa `k8s/auth-service/configmap.yaml` — TLS Postgres bật qua `?sslmode=require` trong URL:
```yaml
data:
  DATASOURCE_URL: "jdbc:postgresql://<cloud-pg-host>:5432/auth_service?sslmode=require"
  REDIS_HOST: "<cloud-redis-host>"
  REDIS_PORT: "6379"          # hoặc cổng TLS của cloud (vd 6380)
  # ... các giá trị khác giữ nguyên
```
Tạo Secret:
```bash
kubectl create secret generic auth-service-secrets \
  --from-literal=DATASOURCE_USERNAME='<cloud-pg-user>' \
  --from-literal=DATASOURCE_PASSWORD='<cloud-pg-pass>' \
  --from-literal=REDIS_PASSWORD='<cloud-redis-pass>'
```

> **Redis TLS + auth-service:** auth-service prod đã hỗ trợ TLS Redis qua
> `spring.data.redis.ssl.enabled: ${REDIS_SSL_ENABLED:false}`. Nếu Redis cloud **bắt buộc TLS**,
> thêm `--from-literal=REDIS_SSL_ENABLED='true'` vào Secret ở trên (và rebuild image nếu bạn
> vừa build từ trước lúc thêm config này). Redis in-cluster/non-TLS thì để mặc định `false`.

### 11.4 Trỏ api-gateway sang Redis cloud
Gateway đã hỗ trợ TLS sẵn (`REDIS_SSL_ENABLED`):
```bash
kubectl create secret generic api-gateway-secrets \
  --from-literal=GATEWAY_SHARED_SECRET='novaplay-gateway-shared-secret' \
  --from-literal=REDIS_HOST='<cloud-redis-host>' \
  --from-literal=REDIS_PORT='6379' \
  --from-literal=REDIS_PASSWORD='<cloud-redis-pass>' \
  --from-literal=REDIS_SSL_ENABLED='true'      # Redis cloud thường bắt TLS
```

### 11.5 Cho cluster truy cập được cloud
- **Allowlist/firewall** của Postgres/Redis cloud phải cho phép IP egress của cluster.
  docker-desktop ra internet bằng IP public của máy bạn → thêm IP đó vào allowlist cloud.
- Pod cần ra internet được (docker-desktop mặc định có).

### 11.6 Deploy
Chạy **Bước 5 (auth-service)** và **Bước 6 (api-gateway)** như bình thường. Không cần
postgres/redis pod, không cần Bước 3.

### (Tùy chọn) ExternalName để giữ nguyên hostname `postgres`/`redis-master`
Nếu không muốn sửa hostname trong config, tạo Service ExternalName trỏ ra cloud:
```yaml
apiVersion: v1
kind: Service
metadata: { name: postgres, namespace: default }
spec: { type: ExternalName, externalName: <cloud-pg-host> }
---
apiVersion: v1
kind: Service
metadata: { name: redis-master, namespace: default }
spec: { type: ExternalName, externalName: <cloud-redis-host> }
```
> ⚠️ Với **TLS**, app kết nối bằng tên `postgres`/`redis-master` nhưng chứng chỉ cloud cấp cho
> hostname thật → dễ lỗi xác thực SNI/hostname. Khi cloud dùng TLS, nên đặt **hostname thật**
> thẳng vào config (cách 11.3/11.4) thay vì ExternalName.

---

## Lưu ý quan trọng

| Vấn đề | Chi tiết |
|--------|----------|
| **NetworkPolicy không chặn ở local** | CNI kindnet của docker-desktop **bỏ qua** NetworkPolicy. Manifest `auth-service/networkpolicy.yaml` đúng và sẽ chặn thật trên cluster prod (Calico/Cilium), nhưng ở local pod khác vẫn gọi thẳng auth-service được. |
| **Dữ liệu ephemeral** | Infra để `persistence=false`. Pod postgres restart → mất schema+seed, phải chạy lại **Bước 3**. |
| **docker-compose vs k8s** | Hai bản triển khai độc lập. Nếu chạy k8s thì nên tắt stack compose (`docker compose ... down`) để khỏi trùng cổng/nhầm lẫn. |
| **OTel `localhost:4318`** | Nếu CHƯA deploy monitoring (Mục 10), agent log lỗi kết nối `localhost:4318` (vô hại) — muốn tắt hẳn: `OTEL_SDK_DISABLED: "true"`. Sau khi làm Mục 10 (trỏ về Tempo + tắt metrics/logs OTLP) thì hết lỗi. |
| **Public key gateway** | Gateway verify JWT bằng `api-gateway/src/main/resources/certs/public.pem` — phải KHỚP `auth-service/.../keys/public.pem`. Đổi key thì đồng bộ cả hai rồi build lại image. |

# Triển khai NovaPlay lên k3s (production)

Tài liệu này chỉ nói về **production**. Môi trường dev chạy bằng Tilt trên Docker Desktop —
xem [README.md](README.md), hai luồng không dùng chung bước nào.

Khác biệt cốt lõi so với dev:

| | Dev (Tilt) | Prod (k3s) |
|---|---|---|
| Apply | từng file `k8s/<svc>/*.yaml` | `kubectl apply -k k8s/overlays/prod` |
| Namespace | `default` | `novaplay` |
| Image | build local, tag `latest` | `ghcr.io/81nhuquynh/<svc>:<sha>` do CI push |
| Postgres / Mongo / Redis | in-cluster | managed (Supabase/Neon, Atlas, Redis Cloud) |
| Kafka | in-cluster | in-cluster |
| Secret | `apply-dev-secrets.sh` từ file env | SealedSecret đã commit |
| Vào từ ngoài | Service LoadBalancer | Ingress (Traefik) + TLS Let's Encrypt |
| NetworkPolicy | **không có hiệu lực** (kindnet bỏ qua) | **có hiệu lực** (kube-router) |

Dòng cuối bảng là thứ hay cắn nhất: một NetworkPolicy thiếu rule chạy hoàn hảo ở local rồi chết ở
prod. Xem mục kiểm chứng ở cuối.

---

## 1. Chuẩn bị hạ tầng ngoài cluster

Làm trước, vì bước 4 cần credential từ đây.

1. **Postgres** (Supabase hoặc Neon) — tạo database rỗng tên `auth_service`, user có quyền tạo
   bảng. **Không cần nạp schema**: Liquibase tự dựng lúc auth-service khởi động, gồm cả hai role
   USER/ADMIN.

   > Lấy connection string ở endpoint **direct**, không phải pooled — Supabase cổng `5432`
   > (không phải `6543`), Neon host không có hậu tố `-pooler`. Outbox dùng `LISTEN/NOTIFY`, thứ
   > không chạy qua PgBouncer transaction mode. Buộc phải dùng pooler thì đặt
   > `AUTH_SERVICE__OUTBOX_LISTEN_ENABLED=false` và `OUTBOX_POLL_INTERVAL=5s` ở bước 4 — outbox
   > chuyển sang cơ chế poll, vẫn đúng, chỉ trễ vài giây.

2. **MongoDB Atlas** (M0 free) — một cluster, 6 database: `user_service`, `movie_service`,
   `media_service`, `notification_service`, `streaming_service`, `config_service`.

3. **Redis Cloud** (free 30MB) — một instance dùng chung. Toàn bộ dữ liệu là cache/TTL ngắn nên
   mất là vô hại.

4. **Object storage** — S3 / Cloudflare R2 / Backblaze B2. **Cả ba** service media, streaming và
   transcoding-worker phải trỏ CÙNG một bucket, nếu không playback trả 404 dù transcode đã xong.

5. **DNS** — bản ghi A của domain trỏ vào IP public của node k3s. Phải xong **trước** bước 5,
   vì Let's Encrypt xác minh bằng cách gọi ngược lại domain đó.

Nhớ bật allowlist IP ở cả ba nhà cung cấp datastore cho IP public của VPS.

---

## 2. Cài k3s và các addon

```bash
# k3s đã kèm sẵn: metrics-server (HPA chạy được ngay), Traefik (ingress), và
# kube-router (ENFORCE NetworkPolicy — khác hẳn Docker Desktop).
curl -sfL https://get.k3s.io | sh -
sudo cat /etc/rancher/k3s/k3s.yaml   # copy về ~/.kube/config trên máy bạn, sửa server: thành IP thật

# cert-manager — cấp chứng chỉ TLS
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.2/cert-manager.yaml
kubectl -n cert-manager rollout status deploy/cert-manager-webhook --timeout=180s

# sealed-secrets — giải mã secret đã commit
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system

# kubeseal CLI (trên máy bạn, không phải trên VPS)
# https://github.com/bitnami-labs/sealed-secrets/releases
```

### Backup sealing key — làm NGAY sau khi cài

```bash
kubectl get secret -n kube-system \
  -l sealedsecrets.bitnami.com/sealed-secrets-key -o yaml > sealed-secrets-key-BACKUP.yaml
```

Cất ở nơi **không phải repo này** (password manager, ổ mã hoá offline). Mất file này mà cluster
cũng mất thì mọi `sealedsecret.yaml` đã commit thành rác không thể phục hồi, và bạn phải tạo lại
từ đầu toàn bộ credential — gồm cả JWT keypair, nghĩa là mọi người dùng bị đăng xuất.
File đã được gitignore sẵn.

---

## 3. Kafka in-cluster

Kafka là datastore duy nhất còn chạy trong cluster (không có free tier managed nào khả dụng).

```bash
kubectl create namespace novaplay
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install kafka bitnami/kafka -n novaplay -f k8s/infra/kafka-values.yaml
```

> **`kafka-values.yaml` hiện tại là cấu hình DEV**: `persistence.enabled=false` và listener
> `PLAINTEXT` không xác thực. Chạy nguyên trạng ở prod nghĩa là mất toàn bộ message mỗi khi pod
> bị tạo lại. Trước khi phục vụ traffic thật, tạo `kafka-values-prod.yaml` bật persistence và
> SASL. `k8s/infra/networkpolicy-datastores.yaml` đã giới hạn ai được kết nối tới cổng 9092, đó
> là lớp phòng thủ tạm thời chứ không thay thế được xác thực.

Namespace `monitoring` (nếu deploy Prometheus) **phải được gắn nhãn `name: monitoring`** — mọi
`networkpolicy.yaml` dùng `namespaceSelector` khớp nhãn đó để cho phép scrape:

```bash
kubectl create namespace monitoring
kubectl label namespace monitoring name=monitoring
```

---

## 4. Secret

```bash
cp k8s/infra/prod-secrets.env.example k8s/infra/prod-secrets.env
# điền credential thật từ bước 1 — file này đã gitignore, KHÔNG BAO GIỜ commit

k8s/infra/scripts/seal-secrets.sh k8s/infra/prod-secrets.env
```

Script sinh `k8s/<svc>/sealedsecret.yaml` (đã mã hoá, **an toàn để commit**) và tự đăng ký chúng
vào kustomization của từng service. Kiểm tra trước khi commit:

```bash
grep -L encryptedData k8s/*/sealedsecret.yaml   # không được in ra file nào
```

Ba giá trị phải giống hệt nhau ở nhiều service — lệch là hỏng theo cách khó chẩn đoán:

| Giá trị | Dùng chung bởi | Triệu chứng khi lệch |
|---|---|---|
| `GATEWAY_SHARED_SECRET` | gateway + 6 service | **Mọi** request trả 403, kể cả duyệt catalog ẩn danh |
| `TRANSCODE_KEY_WRAP_SECRET` | transcoding-worker + streaming | Video transcode xong nhưng không phát được; lỗi hiện ở trình phát |
| JWT keypair | auth-service (private) + gateway (public) | 401 trên mọi request đã đăng nhập — rất giống token hết hạn |

---

## 5. Deploy

```bash
# Sửa domain + email trước:
#   k8s/overlays/prod/ingress.yaml        (novaplay.example.com — 3 chỗ)
#   k8s/overlays/prod/clusterissuer.yaml  (your-email@example.com)

kubectl kustomize k8s/overlays/prod        # xem trước, không apply gì
kubectl apply -k k8s/overlays/prod
kubectl -n novaplay rollout status deploy/auth-service --timeout=300s
```

### Image private

Image mới push lên GHCR mặc định là **private**, và k3s sẽ kẹt ở `ImagePullBackOff`. Hai cách:

**Cách A — để package public** (đơn giản nhất cho project cá nhân): trang repo GitHub → Packages
→ từng package → Package settings → Change visibility → Public.

**Cách B — imagePullSecret**: tạo một GitHub Personal Access Token (classic) với scope
`read:packages`, rồi:

```bash
kubectl create secret docker-registry ghcr \
  -n novaplay \
  --docker-server=ghcr.io \
  --docker-username=81nhuquynh \
  --docker-password='<PAT>'
kubectl patch serviceaccount default -n novaplay \
  -p '{"imagePullSecrets":[{"name":"ghcr"}]}'
```

Patch vào ServiceAccount `default` nghĩa là mọi pod trong namespace tự dùng nó, không phải sửa
từng deployment.

---

## 6. Kiểm chứng

### NetworkPolicy — bài test KHÔNG thể chạy ở local

kindnet của Docker Desktop bỏ qua NetworkPolicy nên local luôn xanh. Sáu cạnh Feign dưới đây đi
thẳng giữa các pod, không qua gateway:

```bash
kubectl -n novaplay exec deploy/streaming-service -- curl -sS -m5 http://movie-service:8600/actuator/health
kubectl -n novaplay exec deploy/streaming-service -- curl -sS -m5 http://media-service:8081/actuator/health
kubectl -n novaplay exec deploy/streaming-service -- curl -sS -m5 http://user-service:8700/actuator/health
kubectl -n novaplay exec deploy/streaming-service -- curl -sS -m5 http://config-service:8500/actuator/health
kubectl -n novaplay exec deploy/transcoding-worker -- curl -sS -m5 http://media-service:8081/actuator/health
kubectl -n novaplay exec deploy/user-service -- curl -sS -m5 http://media-service:8081/actuator/health
```

Timeout = NetworkPolicy đang chặn. Thêm `podSelector` của caller vào `networkpolicy.yaml` của
callee.

Và một cạnh **phải bị chặn** — nếu nó thành công thì policy chưa có hiệu lực:

```bash
kubectl -n novaplay exec deploy/movie-service -- curl -sS -m5 http://user-service:8700/actuator/health
```

### Liquibase

```bash
kubectl -n novaplay logs deploy/auth-service | grep -i liquibase
kubectl -n novaplay exec deploy/auth-service -- env | grep PRIVATE_KEY_PATH
# phải là file:/etc/novaplay/keys/private.pem — KHÔNG phải classpath:
```

Rơi về `classpath:` nghĩa là private key đang được đọc từ trong image, xem lại bước 4.

### Nhiều pod

```bash
kubectl -n novaplay get deploy       # READY phải là 2/2, không phải 1/2
kubectl -n novaplay get hpa          # TARGETS không được là <unknown>
kubectl -n novaplay get pdb
```

`<unknown>` ở HPA nghĩa là metrics-server chưa chạy: `kubectl -n kube-system get deploy metrics-server`.

Xác nhận job dọn dẹp chỉ chạy trên MỘT pod (ShedLock):

```bash
kubectl -n novaplay logs -l app=media-service --tail=500 --prefix | grep "orphaned uploads"
```

Nhiều pod cùng in dòng đó = ShedLock không hoạt động, kiểm tra collection `shedLock` trong Mongo.

### End-to-end

Bài test duy nhất chạm hết mọi thứ: upload video qua `/api/v1/media/upload`, chờ manifest sang
`COMPLETED`, rồi phát qua `/api/v1/streaming/hls/**`. Luồng này đi qua NetworkPolicy (mục 6.1),
JWT keypair (mục 4), và pipeline transcode.

---

## 7. Rollback

Mọi deploy đều ghim image theo commit SHA, nên rollback là trỏ về SHA cũ:

```bash
# Nhanh nhất — chỉ một service:
kubectl -n novaplay rollout undo deploy/movie-service

# Hoặc ghim lại tag rồi apply (giữ Git là nguồn sự thật):
cd k8s/overlays/prod
kustomize edit set image ghcr.io/81nhuquynh/movie-service=ghcr.io/81nhuquynh/movie-service:<sha-cũ>
kubectl apply -k .
```

> **Rollback KHÔNG hoàn tác migration database.** Liquibase đã chạy thì schema đã đổi. Vì thế mọi
> changeset phải tương thích ngược với phiên bản code TRƯỚC nó — thêm cột nullable trước,
> backfill, rồi mới NOT NULL ở lần deploy sau. Deployment dùng `maxUnavailable: 0` nên hai phiên
> bản code luôn chạy song song một lúc trong mỗi lần rollout, kể cả khi không rollback.

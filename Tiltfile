# -*- mode: Python -*-
#
# Khởi động toàn bộ NovaPlay (9 service có k8s manifest) trên Docker Desktop k8s bằng 1 lệnh:
#
#   tilt up
#
# Thay thế runbook thủ công 11 bước cũ ở k8s/infra/README.md (chỉ từng bao phủ auth-service +
# api-gateway). Xem README đó để biết prerequisites, cách bật cloud config, và troubleshooting.
#
# LƯU Ý: file này được viết dựa trên tài liệu API của Tilt, KHÔNG chạy thử được trong lúc soạn
# (môi trường soạn thảo không có `tilt` binary/cluster). Nếu `tilt up` báo lỗi cú pháp Starlark
# hoặc tham số sai ở helm_resource/local_resource, đó nhiều khả năng là lệch phiên bản
# ext://helm_resource — báo lại nội dung lỗi để sửa nhanh.

load('ext://helm_resource', 'helm_resource', 'helm_repo')

# An toàn: chỉ cho phép apply vào cluster Docker Desktop, tránh lỡ tay chạy nhắm cluster khác
# nếu kubeconfig đang trỏ nơi khác (vd một cloud cluster thật).
allow_k8s_contexts('docker-desktop')

# ---------------------------------------------------------------------------
# 0. Settings + preflight
# ---------------------------------------------------------------------------
settings = read_json('tilt-settings.json', default={})
use_cloud_postgres = settings.get('use_cloud_postgres', False)
use_cloud_redis = settings.get('use_cloud_redis', False)
use_cloud_mongo = settings.get('use_cloud_mongo', False)
use_monitoring = settings.get('use_monitoring', True)
cloud_postgres_url = settings.get('cloud_postgres_url', '')
cloud_redis_host = settings.get('cloud_redis_host', '')
cloud_redis_port = settings.get('cloud_redis_port', '6379')
cloud_redis_ssl_enabled = settings.get('cloud_redis_ssl_enabled', True)

if use_cloud_postgres and not cloud_postgres_url:
    fail('tilt-settings.json: use_cloud_postgres=true nhưng cloud_postgres_url rỗng')
if use_cloud_redis and not cloud_redis_host:
    fail('tilt-settings.json: use_cloud_redis=true nhưng cloud_redis_host rỗng')
# use_cloud_mongo không cần 1 host chung như postgres/redis: cả 6 service đã đọc MONGODB_URI là
# một connection string ĐẦY ĐỦ (kể cả cluster host) trực tiếp từ k8s/infra/dev-secrets.env, khác
# với REDIS_HOST/DATASOURCE_URL vốn tách riêng host khỏi credential. Không cần validate gì thêm
# ở đây — thiếu giá trị thì service tự fail lúc khởi động với lỗi rõ ràng từ Spring.

# JWT keypair không thể tự sinh ở đây (auth-service/api-gateway phải dùng CÙNG public key) —
# fail nhanh trước khi build image thay vì để lỗi mơ hồ lộ ra lúc chạy.
local('k8s/infra/scripts/check-jwt-keys.sh')

# ---------------------------------------------------------------------------
# 1. Hạ tầng dùng chung
#    - Postgres/Redis/Kafka: Bitnami Helm chart có sẵn (k8s/infra/*-values.yaml)
#    - Mongo/MinIO/Mailhog: viết tay (k8s/infra/mongodb|minio.yaml|mailhog.yaml) — không chart
#      Bitnami nào khớp yêu cầu 1-pod/không-persistence/URI phẳng mà 6 service đang cần
# ---------------------------------------------------------------------------
helm_repo('bitnami', 'https://charts.bitnami.com/bitnami', labels=['infra'])

if not use_cloud_postgres:
    helm_resource(
        'postgres',
        'bitnami/postgresql',
        namespace='default',
        flags=['--values=k8s/infra/postgresql-values.yaml', '--wait'],
        resource_deps=['bitnami'],
        labels=['infra'],
    )

if not use_cloud_redis:
    helm_resource(
        'redis',
        'bitnami/redis',
        namespace='default',
        flags=['--values=k8s/infra/redis-values.yaml', '--wait'],
        resource_deps=['bitnami'],
        labels=['infra'],
    )

# Kafka luôn cài in-cluster (không có toggle "cloud kafka" trong phạm vi task này — xem README).
helm_resource(
    'kafka',
    'bitnami/kafka',
    namespace='default',
    flags=['--values=k8s/infra/kafka-values.yaml', '--wait'],
    resource_deps=['bitnami'],
    labels=['infra'],
)

if not use_cloud_mongo:
    k8s_yaml(['k8s/infra/mongodb/deployment.yaml', 'k8s/infra/mongodb/service.yaml'])
    k8s_resource('mongodb', labels=['infra'], port_forwards=['27017:27017'])
# Cloud Mongo (vd Atlas): không deploy Mongo in-cluster. Không cần host chung ở đây — mỗi service
# đọc MONGODB_URI riêng (đã trỏ Atlas) trực tiếp từ Secret, xem k8s/infra/dev-secrets.env.

k8s_yaml('k8s/infra/minio.yaml')
k8s_resource('media-minio', labels=['infra'], port_forwards=['9010:9000', '9011:9001'])

k8s_yaml('k8s/infra/mailhog.yaml')
k8s_resource('mailhog', labels=['infra'], port_forwards=['1025:1025', '8025:8025'])

# ---------------------------------------------------------------------------
# 1b. Monitoring: Prometheus/Grafana/Alertmanager (kube-prometheus-stack), Loki, Tempo, Alloy.
#     Namespace "monitoring" riêng, đúng theo README cũ (mục Monitoring) nhưng giờ tự động hoá
#     toàn bộ thay vì chạy tay. CHƯA kiểm chứng bằng cluster thật lúc soạn file này — nếu lỗi ở
#     bước nào, khả năng cao là lệch key values.yaml so với version chart cài trên máy bạn.
# ---------------------------------------------------------------------------
if use_monitoring:
    k8s_yaml('k8s/infra/monitoring/namespace.yaml')
    k8s_resource('monitoring', new_name='monitoring-namespace', labels=['monitoring'])

    helm_repo('prometheus-community', 'https://prometheus-community.github.io/helm-charts', labels=['monitoring'])
    helm_repo('grafana-charts', 'https://grafana.github.io/helm-charts', labels=['monitoring'])

    helm_resource(
        'kube-prom',
        'prometheus-community/kube-prometheus-stack',
        namespace='monitoring',
        flags=['--values=k8s/infra/monitoring/kube-prometheus-stack-values.yaml', '--wait', '--timeout=10m'],
        resource_deps=['prometheus-community', 'monitoring-namespace'],
        port_forwards=['3000:80', '9090:9090'],
        labels=['monitoring'],
    )

    helm_resource(
        'loki',
        'grafana-charts/loki',
        namespace='monitoring',
        flags=['--values=k8s/infra/monitoring/loki-values.yaml', '--wait'],
        resource_deps=['grafana-charts', 'monitoring-namespace'],
        port_forwards=['3100:3100'],
        labels=['monitoring'],
    )

    helm_resource(
        'tempo',
        'grafana-charts/tempo',
        namespace='monitoring',
        flags=['--values=k8s/infra/monitoring/tempo-values.yaml', '--wait'],
        resource_deps=['grafana-charts', 'monitoring-namespace'],
        port_forwards=['3200:3100'],
        labels=['monitoring'],
    )

    helm_resource(
        'alloy',
        'grafana-charts/alloy',
        namespace='monitoring',
        flags=['--values=k8s/infra/monitoring/alloy-values.yaml', '--wait'],
        # Chờ Loki lên trước — Alloy đẩy log thẳng về loki.monitoring.svc, không tự retry dài hơi
        # lúc mới khởi động.
        resource_deps=['loki'],
        labels=['monitoring'],
    )

    k8s_yaml('k8s/infra/monitoring/podmonitor.yaml')
    k8s_resource(
        'novaplay-podmonitor',
        objects=['novaplay-apps:podmonitor'],
        resource_deps=['kube-prom'],
        labels=['monitoring'],
    )

# ---------------------------------------------------------------------------
# 2. Secrets + seed/init — local_resource idempotent, an toàn chạy lại mỗi `tilt up`
#    (Postgres/Mongo mất dữ liệu mỗi lần pod bị tạo lại vì persistence tắt ở dev).
# ---------------------------------------------------------------------------
local_resource(
    'dev-secrets',
    cmd='k8s/infra/scripts/apply-dev-secrets.sh',
    labels=['infra'],
)

# KHÔNG còn resource 'postgres-seed'. Cả schema lẫn hai role USER/ADMIN nay do Liquibase dựng lúc
# auth-service khởi động (auth-service/src/main/resources/db/changelog/) — giống hệt nhau ở dev,
# ở cloud Postgres, và ở prod. Không còn bước seed thủ công nào cho Postgres.

if not use_cloud_mongo:
    local_resource(
        'mongo-config-flags-seed',
        cmd='k8s/infra/scripts/mongo-config-flags-seed.sh',
        resource_deps=['mongodb'],
        labels=['infra'],
    )
# Cloud Mongo: bỏ qua — seed này chỉ để tiện dev (config-service KHÔNG bắt buộc phải có document
# này, media-service tự fallback default-storage-provider nếu thiếu). Muốn seed thủ công trên
# Atlas: mongosh "<MONGODB_URI của config-service>" --eval '...' (xem nội dung script để copy).

local_resource(
    'minio-bucket-init',
    cmd='k8s/infra/scripts/minio-bucket-init.sh',
    resource_deps=['media-minio'],
    labels=['infra'],
)

# ---------------------------------------------------------------------------
# 3. Helper: apply deployment.yaml kèm env override riêng cho dev (KHÔNG sửa file đã commit).
#    Dùng cho: MinIO endpoint/creds (media/streaming/transcoding-worker — configmap.yaml hiện
#    trỏ thẳng AWS S3 thật cho prod), Mailhog host/port (notification-service — configmap.yaml
#    hiện trỏ smtp.gmail.com cho prod), và override cloud-config khi bật toggle.
# ---------------------------------------------------------------------------
def deployment_with_env_overrides(path, name, extra_env):
    objects = read_yaml_stream(path)
    for obj in objects:
        if obj.get('kind') != 'Deployment' or obj.get('metadata', {}).get('name') != name:
            continue
        for c in obj['spec']['template']['spec']['containers']:
            if c.get('name') != name:
                continue
            env = c.get('env', [])
            for k, v in extra_env.items():
                env.append({'name': k, 'value': v})
            c['env'] = env
    return encode_yaml_stream(objects)

# ---------------------------------------------------------------------------
# 4. Danh sách 9 service — ánh xạ đúng depends_on/condition của
#    docker-compose/qa/docker-compose.yml sang resource_deps của Tilt.
# ---------------------------------------------------------------------------
# Chỉ chờ Postgres lên; schema do chính auth-service dựng bằng Liquibase lúc khởi động.
PG_DEPS = [] if use_cloud_postgres else ['postgres']
REDIS_DEPS = [] if use_cloud_redis else ['redis']
MONGO_DEPS = [] if use_cloud_mongo else ['mongodb']

SERVICES = {
    'auth-service': dict(
        image='ghcr.io/81nhuquynh/auth-service', context='auth-service', dockerfile='auth-service/Dockerfile',
        port=8000,
        resource_deps=PG_DEPS + REDIS_DEPS + ['kafka', 'dev-secrets'],
    ),
    'config-service': dict(
        image='ghcr.io/81nhuquynh/config-service', context='.', dockerfile='config-service/Dockerfile',
        port=8500,
        resource_deps=MONGO_DEPS + ['dev-secrets'],
    ),
    'user-service': dict(
        image='ghcr.io/81nhuquynh/user-service', context='.', dockerfile='user-service/Dockerfile',
        port=8700,
        resource_deps=MONGO_DEPS + REDIS_DEPS + ['dev-secrets'],
    ),
    'movie-service': dict(
        image='ghcr.io/81nhuquynh/movie-service', context='.', dockerfile='movie-service/Dockerfile',
        port=8600,
        resource_deps=MONGO_DEPS + REDIS_DEPS + ['dev-secrets'],
    ),
    'notification-service': dict(
        image='ghcr.io/81nhuquynh/notification-service', context='.', dockerfile='notification-service/Dockerfile',
        port=8900,
        resource_deps=MONGO_DEPS + ['kafka', 'mailhog', 'dev-secrets'],
    ),
    # config-service CHỈ cần "started" trong compose (media-service tự fallback default nếu
    # config-service chưa sẵn sàng) — cố tình KHÔNG thêm 'config-service' vào resource_deps,
    # Tilt không có khái niệm "started nhưng chưa ready" nên bỏ qua dep là bản dịch đúng.
    'media-service': dict(
        image='ghcr.io/81nhuquynh/media-service', context='.', dockerfile='media-service/Dockerfile',
        port=8081,
        resource_deps=MONGO_DEPS + ['kafka', 'minio-bucket-init', 'dev-secrets'],
    ),
    'transcoding-worker': dict(
        image='ghcr.io/81nhuquynh/transcoding-worker', context='.', dockerfile='transcoding-worker/Dockerfile',
        port=8400, has_service=False,
        resource_deps=['kafka', 'minio-bucket-init', 'media-service', 'dev-secrets'],
    ),
    'streaming-service': dict(
        image='ghcr.io/81nhuquynh/streaming-service', context='.', dockerfile='streaming-service/Dockerfile',
        port=8200,
        resource_deps=MONGO_DEPS + REDIS_DEPS + ['minio-bucket-init', 'movie-service', 'media-service', 'dev-secrets'],
    ),
    # compose cố tình comment out depends_on của api-gateway (gateway route 5xx graceful cho tới
    # khi target sẵn sàng). Ở đây khai báo đủ resource_deps: mục tiêu của Tilt là "bảng UI xanh
    # hết = hệ thống thật sự sẵn sàng", tránh dev gọi thử qua gateway quá sớm rồi thấy 502/503
    # không rõ lý do.
    'api-gateway': dict(
        image='ghcr.io/81nhuquynh/api-gateway', context='api-gateway', dockerfile='api-gateway/Dockerfile',
        port=8072,
        resource_deps=[
            'auth-service', 'user-service', 'movie-service', 'media-service',
            'notification-service', 'streaming-service', 'dev-secrets',
        ],
    ),
}

MINIO_ENV = {
    'STORAGE_PROVIDERS_AWSS3_ENDPOINT': 'http://media-minio:9000',
    'AWS_ACCESS_KEY_ID': 'media-dev',
    'AWS_SECRET_ACCESS_KEY': 'media-dev-secret',
}
MAILHOG_ENV = {'MAIL_HOST': 'mailhog', 'MAIL_PORT': '1025'}

EXTRA_ENV = {
    'media-service': dict(MINIO_ENV),
    'streaming-service': dict(MINIO_ENV),
    'transcoding-worker': dict(MINIO_ENV),
    'notification-service': dict(MAILHOG_ENV),
}

if use_monitoring:
    # auth-service/api-gateway ĐÃ hardcode 3 biến này thẳng trong deployment.yaml (trỏ sẵn
    # tempo.monitoring.svc:4318) — KHÔNG thêm ở đây nữa, tránh 2 entry env trùng tên trong cùng
    # container. 7 service còn lại chưa có biến này ở đâu cả (mặc định OTEL agent bắn thử
    # localhost:4318, log lỗi vô hại) — bật đúng theo README cũ mục 10.5.
    OTEL_ENV = {
        'OTEL_EXPORTER_OTLP_ENDPOINT': 'http://tempo.monitoring.svc:4318',
        'OTEL_METRICS_EXPORTER': 'none',
        'OTEL_LOGS_EXPORTER': 'none',
    }
    for svc in [
        'config-service', 'user-service', 'movie-service', 'notification-service',
        'media-service', 'transcoding-worker', 'streaming-service',
    ]:
        EXTRA_ENV.setdefault(svc, {}).update(OTEL_ENV)

if use_cloud_postgres:
    EXTRA_ENV.setdefault('auth-service', {})['DATASOURCE_URL'] = cloud_postgres_url

if use_cloud_redis:
    # auth/user/movie/notification-service: REDIS_HOST nằm ở ConfigMap -> cần override ở đây.
    # api-gateway/streaming-service: REDIS_HOST nằm ở Secret, đọc thẳng từ dev-secrets.env
    # (API_GATEWAY__REDIS_HOST / STREAMING_SERVICE__REDIS_HOST) — không cần đụng tới ở đây, kể cả
    # REDIS_SSL_ENABLED (cả hai đã có key này sẵn trong secret.example.yaml của chúng).
    ssl_value = 'true' if cloud_redis_ssl_enabled else 'false'
    for svc in ['auth-service', 'user-service', 'movie-service', 'notification-service']:
        EXTRA_ENV.setdefault(svc, {})
        EXTRA_ENV[svc]['REDIS_HOST'] = cloud_redis_host
        EXTRA_ENV[svc]['REDIS_PORT'] = cloud_redis_port
        EXTRA_ENV[svc]['REDIS_SSL_ENABLED'] = ssl_value

for name, cfg in SERVICES.items():
    docker_build(cfg['image'], context=cfg['context'], dockerfile=cfg['dockerfile'])

    base = 'k8s/%s' % name
    extra_env = EXTRA_ENV.get(name)
    if extra_env:
        k8s_yaml(deployment_with_env_overrides(base + '/deployment.yaml', name, extra_env))
    else:
        k8s_yaml(base + '/deployment.yaml')

    static_files = [base + '/configmap.yaml']
    if cfg.get('has_service', True):
        static_files.append(base + '/service.yaml')
    static_files.append(base + '/networkpolicy.yaml')
    k8s_yaml(static_files)

    if name == 'api-gateway':
        # KHÔNG port-forward: Service type LoadBalancer trên Docker Desktop đã tự expose thẳng
        # ra localhost:80 (đúng như ví dụ curl http://localhost/... trong README) — port-forward
        # thêm ở đây sẽ tranh chấp cổng 80 với binding có sẵn của Docker Desktop.
        port_forwards = []
    else:
        port_forwards = ['%d:%d' % (cfg['port'], cfg['port'])]

    k8s_resource(
        name,
        resource_deps=cfg['resource_deps'],
        port_forwards=port_forwards,
        labels=['app'],
    )

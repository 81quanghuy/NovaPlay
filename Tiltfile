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

# Tilt mặc định huỷ MỖI lệnh apply sau 30s. helm_resource chạy `helm upgrade --install --wait`,
# tức là chờ tới khi pod của chart Ready — không chart nào kịp 30s (alloy ~1 phút vì phải pull
# image; kube-prometheus-stack ở chế độ in-cluster còn lâu hơn nhiều, chính nó đã tự xin
# --timeout=10m). Đây là trần cho TỪNG resource, không phải tổng thời gian `tilt up`, nên đặt
# rộng không làm chậm gì cả — resource xong sớm thì đi tiếp sớm.
update_settings(k8s_upsert_timeout_secs=600)

# ---------------------------------------------------------------------------
# 0. Settings + preflight
# ---------------------------------------------------------------------------
settings = read_json('tilt-settings.json', default={})
use_cloud_postgres = settings.get('use_cloud_postgres', False)
use_cloud_redis = settings.get('use_cloud_redis', False)
use_cloud_mongo = settings.get('use_cloud_mongo', False)
use_monitoring = settings.get('use_monitoring', True)
use_cloud_kafka = settings.get('use_cloud_kafka', False)
use_cloud_storage = settings.get('use_cloud_storage', False)
use_grafana_cloud = settings.get('use_grafana_cloud', False)
dev_scale_down = settings.get('dev_scale_down', True)
cloud_postgres_url = settings.get('cloud_postgres_url', '')
cloud_kafka_bootstrap = settings.get('cloud_kafka_bootstrap', '')
cloud_redis_host = settings.get('cloud_redis_host', '')
cloud_redis_port = settings.get('cloud_redis_port', '6379')
cloud_redis_ssl_enabled = settings.get('cloud_redis_ssl_enabled', True)

if use_cloud_postgres and not cloud_postgres_url:
    fail('tilt-settings.json: use_cloud_postgres=true nhưng cloud_postgres_url rỗng')
if use_cloud_redis and not cloud_redis_host:
    fail('tilt-settings.json: use_cloud_redis=true nhưng cloud_redis_host rỗng')
if use_cloud_kafka and not cloud_kafka_bootstrap:
    fail('tilt-settings.json: use_cloud_kafka=true nhưng cloud_kafka_bootstrap rỗng ' +
         '(dạng "novaplay-kafka-<project>.<region>.aivencloud.com:10550")')
if use_grafana_cloud and not use_monitoring:
    fail('tilt-settings.json: use_grafana_cloud=true nhưng use_monitoring=false — ' +
         'Alloy là thứ đẩy telemetry lên Grafana Cloud, tắt monitoring là không gửi gì cả')
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

# Kafka: in-cluster (Bitnami) HOẶC managed (Aiven) tuỳ use_cloud_kafka.
# Aiven free tier có quota CỨNG 5 topic x 2 partition và KHÔNG cho auto-create topic — Tiltfile
# vì thế đặt KAFKA_ADMIN_AUTO_CREATE=false cho mọi service (xem KAFKA_ENV bên dưới), topic phải
# tạo tay trong Aiven Console. Danh sách topic: k8s/infra/README.md.
if not use_cloud_kafka:
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

# MinIO chỉ tồn tại khi KHÔNG dùng object storage cloud. Với use_cloud_storage=true, cả ba
# service media/streaming/transcoding-worker đọc/ghi thẳng Cloudflare R2 bằng credential trong
# Secret (R2_* ở dev-secrets.env) — không còn endpoint override nào trỏ vào cụm.
if not use_cloud_storage:
    k8s_yaml('k8s/infra/minio.yaml')
    k8s_resource('media-minio', labels=['infra'], port_forwards=['9010:9000', '9011:9001'])

k8s_yaml('k8s/infra/mailhog.yaml')
k8s_resource('mailhog', labels=['infra'], port_forwards=['1025:1025', '8025:8025'])

# ---------------------------------------------------------------------------
# 1b. Monitoring — hai chế độ, chọn bằng use_grafana_cloud:
#     - true : CHỈ Alloy chạy trong cụm, đẩy metrics/logs/traces thẳng lên Grafana Cloud.
#              Bỏ được 4 thành phần nặng nhất (~2-3GB RAM) khỏi node laptop.
#     - false: full stack in-cluster như trước — kube-prometheus-stack + Loki + Tempo + Alloy.
#     Cả hai chế độ đều nhận OTLP tại alloy.monitoring.svc:4318 (xem OTEL_ENV bên dưới).
# ---------------------------------------------------------------------------
if use_monitoring:
    k8s_yaml('k8s/infra/monitoring/namespace.yaml')
    # Namespace KHÔNG phải workload nên Tilt không tự sinh resource cho nó — phải khai bằng
    # objects=['<tên>:<kind>'] kèm new_name, giống cách podmonitor được khai bên dưới. Viết
    # k8s_resource('monitoring', ...) sẽ fail lúc load Tiltfile với "unknown resource".
    k8s_resource(
        new_name='monitoring-namespace',
        objects=['monitoring:namespace'],
        labels=['monitoring'],
    )

    helm_repo('grafana-charts', 'https://grafana.github.io/helm-charts', labels=['monitoring'])

    if use_grafana_cloud:
        # Chế độ Grafana Cloud: KHÔNG cài Prometheus/Grafana/Loki/Tempo trong cụm nữa (4 thành
        # phần nặng nhất, ~2-3GB RAM trên node laptop). Chỉ còn Alloy làm điểm ra DUY NHẤT:
        # scrape /actuator/prometheus -> remote_write, tail log pod -> Loki, nhận OTLP -> Tempo,
        # tất cả đều là endpoint Grafana Cloud. Credential nằm ở Secret grafana-cloud-secrets
        # (namespace monitoring, do apply-dev-secrets.sh tạo) nên alloy phải chờ dev-secrets.
        #
        # Deployment 1 replica chứ không DaemonSet: DaemonSet nghĩa là MỖI node đều scrape TOÀN
        # BỘ pod -> metric nhân đôi khi cụm có nhiều node, và 10k active series của free tier hết
        # rất nhanh. Log vẫn đủ vì loki.source.kubernetes đọc qua Kubernetes API, không phải đọc
        # file trên từng node.
        helm_resource(
            'alloy',
            'grafana-charts/alloy',
            namespace='monitoring',
            flags=['--values=k8s/infra/monitoring/alloy-cloud-values.yaml', '--wait'],
            resource_deps=['grafana-charts', 'monitoring-namespace', 'dev-secrets'],
            labels=['monitoring'],
        )
    else:
        helm_repo('prometheus-community', 'https://prometheus-community.github.io/helm-charts', labels=['monitoring'])

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
            # Chờ Loki lên trước — Alloy đẩy log thẳng về loki.monitoring.svc, không tự retry dài
            # hơi lúc mới khởi động.
            resource_deps=['loki', 'tempo'],
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

# Chạy ở CẢ HAI chế độ Mongo. Trước đây bước này bị bỏ hẳn khi use_cloud_mongo=true, nên trên
# Atlas collection config_flags rỗng — media-service phải rơi về DEFAULT_STORAGE_PROVIDER, và
# streaming-service không có cờ delivery-mode nào để đọc.
#
# Truyền provider để cờ media-storage-provider khớp DEFAULT_STORAGE_PROVIDER mà media-service
# nhận: cờ trong Mongo THẮNG biến môi trường, lệch nhau là upload mới đi vào provider không có
# credential.
local_resource(
    'mongo-config-flags-seed',
    cmd='k8s/infra/scripts/mongo-config-flags-seed.sh %s %s' % (
        'cloudflare-r2' if use_cloud_storage else 'aws-s3',
        'cloud' if use_cloud_mongo else 'in-cluster'),
    # Cloud: cần Secret config-service-secrets (Job đọc MONGODB_URI từ đó) -> chờ dev-secrets.
    # In-cluster: chờ chính pod mongodb.
    resource_deps=['dev-secrets'] if use_cloud_mongo else ['mongodb'],
    labels=['infra'],
)
# Cloud Mongo: bỏ qua — seed này chỉ để tiện dev (config-service KHÔNG bắt buộc phải có document
# này, media-service tự fallback default-storage-provider nếu thiếu). Muốn seed thủ công trên
# Atlas: mongosh "<MONGODB_URI của config-service>" --eval '...' (xem nội dung script để copy).

if not use_cloud_storage:
    local_resource(
        'minio-bucket-init',
        cmd='k8s/infra/scripts/minio-bucket-init.sh',
        resource_deps=['media-minio'],
        labels=['infra'],
    )
# R2: bucket tạo sẵn trong Cloudflare dashboard, không có bước init tương đương ở đây.

# ---------------------------------------------------------------------------
# 3. Helper: apply deployment.yaml với các chỉnh sửa CHỈ dành cho dev (KHÔNG sửa file đã commit).
#    - env override: MinIO/R2, Mailhog, Kafka Aiven, cloud Postgres/Redis, OTLP endpoint.
#    - dev_scale_down: 1 replica + hạ request cho vừa một cái laptop.
# ---------------------------------------------------------------------------
# Manifest đã commit mô tả một cụm nhiều node: replicas 2 và transcoding-worker xin hẳn
# 2 CPU / 4Gi. Cộng lại là 5.7 vCPU + 11.5 GiB *requests* — scheduler đặt chỗ trước từng đó, nên
# trên VM Docker Desktop mặc định (thường 8GB) sẽ có pod đứng Pending mãi với "Insufficient
# memory", một triệu chứng rất dễ tưởng nhầm là image build hỏng.
#
# Chỉ hạ REQUESTS, cố ý không đụng tới limits: JVM và ffmpeg đọc *limit* để biết trần bộ nhớ
# (cgroup), nên chúng vẫn burst được đúng như cũ — thay đổi ở đây thuần tuý là chuyện xếp chỗ.
# HPA cũng không mâu thuẫn: Tiltfile không apply hpa.yaml (xem static_files bên dưới).
DEV_CPU_REQUEST_CAP_M = 250
DEV_MEM_REQUEST_CAP_MI = 512

def _cpu_millis(v):
    if v.endswith('m'):
        return int(v[:-1])
    return int(v) * 1000

def _mem_mib(v):
    if v.endswith('Gi'):
        return int(v[:-2]) * 1024
    if v.endswith('Mi'):
        return int(v[:-2])
    fail('Không đọc được resources.requests.memory = %s (chỉ hỗ trợ Mi/Gi)' % v)

def deployment_for_dev(path, name, extra_env):
    objects = read_yaml_stream(path)
    for obj in objects:
        if obj.get('kind') != 'Deployment' or obj.get('metadata', {}).get('name') != name:
            continue
        if dev_scale_down:
            obj['spec']['replicas'] = 1
        for c in obj['spec']['template']['spec']['containers']:
            if c.get('name') != name:
                continue
            env = c.get('env', [])
            for k, v in extra_env.items():
                env.append({'name': k, 'value': v})
            c['env'] = env

            if dev_scale_down and 'resources' in c and 'requests' in c['resources']:
                req = c['resources']['requests']
                if 'cpu' in req and _cpu_millis(req['cpu']) > DEV_CPU_REQUEST_CAP_M:
                    req['cpu'] = '%dm' % DEV_CPU_REQUEST_CAP_M
                if 'memory' in req and _mem_mib(req['memory']) > DEV_MEM_REQUEST_CAP_MI:
                    req['memory'] = '%dMi' % DEV_MEM_REQUEST_CAP_MI
    return encode_yaml_stream(objects)

# ---------------------------------------------------------------------------
# 4. Danh sách 9 service — ánh xạ đúng depends_on/condition của
#    docker-compose/qa/docker-compose.yml sang resource_deps của Tilt.
# ---------------------------------------------------------------------------
# Chỉ chờ Postgres lên; schema do chính auth-service dựng bằng Liquibase lúc khởi động.
PG_DEPS = [] if use_cloud_postgres else ['postgres']
REDIS_DEPS = [] if use_cloud_redis else ['redis']
MONGO_DEPS = [] if use_cloud_mongo else ['mongodb']
KAFKA_DEPS = [] if use_cloud_kafka else ['kafka']
STORAGE_DEPS = [] if use_cloud_storage else ['minio-bucket-init']

SERVICES = {
    'auth-service': dict(
        image='ghcr.io/81nhuquynh/auth-service', context='auth-service', dockerfile='auth-service/Dockerfile',
        port=8000,
        resource_deps=PG_DEPS + REDIS_DEPS + KAFKA_DEPS + ['dev-secrets'],
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
        resource_deps=MONGO_DEPS + KAFKA_DEPS + ['mailhog', 'dev-secrets'],
    ),
    # config-service CHỈ cần "started" trong compose (media-service tự fallback default nếu
    # config-service chưa sẵn sàng) — cố tình KHÔNG thêm 'config-service' vào resource_deps,
    # Tilt không có khái niệm "started nhưng chưa ready" nên bỏ qua dep là bản dịch đúng.
    'media-service': dict(
        image='ghcr.io/81nhuquynh/media-service', context='.', dockerfile='media-service/Dockerfile',
        port=8081,
        resource_deps=MONGO_DEPS + KAFKA_DEPS + STORAGE_DEPS + ['dev-secrets'],
    ),
    'transcoding-worker': dict(
        image='ghcr.io/81nhuquynh/transcoding-worker', context='.', dockerfile='transcoding-worker/Dockerfile',
        port=8400, has_service=False,
        resource_deps=KAFKA_DEPS + STORAGE_DEPS + ['media-service', 'dev-secrets'],
    ),
    'streaming-service': dict(
        image='ghcr.io/81nhuquynh/streaming-service', context='.', dockerfile='streaming-service/Dockerfile',
        port=8200,
        resource_deps=MONGO_DEPS + REDIS_DEPS + STORAGE_DEPS + ['movie-service', 'media-service', 'dev-secrets'],
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
    'notification-service': dict(MAILHOG_ENV),
}

if use_cloud_storage:
    # Cloudflare R2. Credential (R2_ACCOUNT_ID/R2_BUCKET_NAME/R2_ACCESS_KEY_ID/
    # R2_SECRET_ACCESS_KEY) đến từ Secret của từng service, KHÔNG đặt ở đây — file này được
    # commit. Hai biến dưới đây không nhạy cảm:
    #
    # - DEFAULT_STORAGE_PROVIDER: chỉ là DEFAULT của cờ OpenFeature "media-storage-provider".
    #   Nếu collection config_flags trên Mongo đã có document đó với value "aws-s3" thì cờ THẮNG
    #   biến này và upload mới vẫn đi vào S3 — xem StorageProviderResolver, và mục R2 trong
    #   k8s/infra/README.md để biết lệnh sửa cờ trên Atlas.
    # - AWS_SQS_ENABLED=false: R2 không phát S3 event nào vào SQS, để true là media-service giữ
    #   một consumer AWS thật vô nghĩa (và là chỗ duy nhất còn cần credential AWS).
    #
    # streaming-service/transcoding-worker KHÔNG cần biến nào ở đây: provider của chúng đọc từ
    # chính manifest/event (StorageProvider.fromName), tức là theo đúng provider mà media-service
    # đã ghi lúc upload.
    EXTRA_ENV.setdefault('media-service', {}).update({
        'DEFAULT_STORAGE_PROVIDER': 'cloudflare-r2',
        'AWS_SQS_ENABLED': 'false',
    })
else:
    for svc in ['media-service', 'streaming-service', 'transcoding-worker']:
        EXTRA_ENV.setdefault(svc, {}).update(MINIO_ENV)

if use_cloud_kafka:
    # KAFKA_SASL_USERNAME/KAFKA_SASL_PASSWORD nằm ở Secret của từng service (dev-secrets.env),
    # CA của Aiven nằm ở Secret aiven-kafka-ca mount vào /etc/novaplay/kafka/ca.pem — cả hai
    # KHÔNG đi qua đây. Chỉ hai công tắc không nhạy cảm ở lại file này.
    for svc in ['auth-service', 'notification-service', 'user-service',
                'media-service', 'transcoding-worker']:
        EXTRA_ENV.setdefault(svc, {}).update({
            'KAFKA_SECURITY_PROTOCOL': 'SASL_SSL',
            # true kể từ khi plan Aiven được nâng (không còn quota 5 topic x 2 partition của free
            # tier): KafkaAdmin của mỗi service tự gọi AdminClient.createTopics cho các NewTopic
            # bean lúc khởi động, gồm CẢ 5 topic .DLT — nhờ đó message hỏng có chỗ để rơi vào
            # thay vì bị poll lại vô hạn.
            #
            # Đây là createTopics tường minh, KHÁC với broker setting auto_create_topics_enable
            # của Aiven (vẫn false) — cái đó chỉ chi phối việc tự tạo topic khi produce vào topic
            # lạ. Đặt lại 'false' nếu quay về free tier hoặc muốn quản topic hoàn toàn bằng tay.
            'KAFKA_ADMIN_AUTO_CREATE': 'true',
        })
    # auth/notification/user: KAFKA_BOOTSTRAP_SERVERS nằm ở ConfigMap -> override ở đây.
    # media/transcoding-worker: nằm ở Secret -> đổi thẳng trong k8s/infra/dev-secrets.env.
    for svc in ['auth-service', 'notification-service', 'user-service']:
        EXTRA_ENV.setdefault(svc, {})['KAFKA_BOOTSTRAP_SERVERS'] = cloud_kafka_bootstrap

if use_monitoring:
    # auth-service/api-gateway ĐÃ hardcode 3 biến này thẳng trong deployment.yaml (nay trỏ
    # alloy.monitoring.svc:4318) — KHÔNG thêm ở đây nữa, tránh 2 entry env trùng tên trong cùng
    # container. 7 service còn lại chưa có biến này ở đâu cả (mặc định OTEL agent bắn thử
    # localhost:4318, log lỗi vô hại) — bật đúng theo README cũ mục 10.5.
    OTEL_ENV = {
        # Alloy là điểm nhận OTLP DUY NHẤT ở cả hai chế độ (in-cluster và Grafana Cloud) — nó
        # forward tiếp về Tempo trong cụm hoặc lên Grafana Cloud tuỳ values file. Trước đây biến
        # này trỏ thẳng tempo.monitoring.svc, nên bật chế độ cloud là mất trace mà không có lỗi.
        'OTEL_EXPORTER_OTLP_ENDPOINT': 'http://alloy.monitoring.svc:4318',
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
    extra_env = EXTRA_ENV.get(name, {})
    if extra_env or dev_scale_down:
        k8s_yaml(deployment_for_dev(base + '/deployment.yaml', name, extra_env))
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

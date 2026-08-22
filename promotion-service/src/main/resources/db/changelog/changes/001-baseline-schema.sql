--liquibase formatted sql

-- Baseline dựng từ entity Coupon / CouponRedemption và AbstractBaseEntity.
--
-- Kiểu cột theo đúng cách Hibernate 6 ánh xạ, để `ddl-auto: validate` không kêu lệch:
--   UUID                  -> uuid
--   BigDecimal(19,2)      -> numeric(19,2)
--   Instant               -> timestamp(6) with time zone
--   enum @Enumerated(STRING) -> varchar(255)
--   String (không @Column length) -> varchar(255)

--changeset novaplay:001-coupons
CREATE TABLE coupons (
    id                        uuid NOT NULL,
    code                      varchar(255) NOT NULL,
    type                      varchar(255) NOT NULL,
    value                     numeric(19,2) NOT NULL,
    min_order_amount          numeric(19,2),
    max_discount_amount       numeric(19,2),
    -- jsonb thô: entity khai kiểu String có chủ đích, việc (de)serialize thuộc tầng service.
    applicable_plan_ids       jsonb,
    start_at                  timestamp(6) with time zone NOT NULL,
    end_at                    timestamp(6) with time zone NOT NULL,
    total_redemption_limit    integer,
    redeemed_count            integer NOT NULL DEFAULT 0,
    per_user_redemption_limit integer NOT NULL DEFAULT 1,
    restricted_to_user_email  varchar(255),
    active                    boolean NOT NULL DEFAULT true,
    created_at                timestamp(6) with time zone,
    created_by                varchar(255),
    updated_at                timestamp(6) with time zone,
    updated_by                varchar(255),
    CONSTRAINT coupons_pkey PRIMARY KEY (id),
    CONSTRAINT uk_coupon_code UNIQUE (code)
);
-- Tra coupon còn hiệu lực là câu chạy nhiều nhất; lọc theo active rồi mới tới khoảng thời gian.
CREATE INDEX idx_coupon_active_window ON coupons USING btree (active, start_at, end_at);
--rollback DROP TABLE coupons;

--changeset novaplay:002-coupon-redemptions
CREATE TABLE coupon_redemptions (
    id              uuid NOT NULL,
    coupon_id       uuid NOT NULL,
    user_email      varchar(255) NOT NULL,
    plan_id         varchar(255),
    discount_amount numeric(19,2) NOT NULL,
    status          varchar(255) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    redeemed_at     timestamp(6) with time zone NOT NULL,
    cancelled_at    timestamp(6) with time zone,
    created_at      timestamp(6) with time zone,
    created_by      varchar(255),
    updated_at      timestamp(6) with time zone,
    updated_by      varchar(255),
    CONSTRAINT coupon_redemptions_pkey PRIMARY KEY (id),
    -- UNIQUE này KHÔNG phải để tối ưu: CouponServiceImpl#redeem dựa vào chính nó để Postgres
    -- chặn race giữa hai request mang cùng idempotency key. Gỡ nó ra là mở lại lỗ đổi coupon
    -- hai lần khi client retry.
    CONSTRAINT uk_redemption_idempotency_key UNIQUE (idempotency_key)
);
-- Đếm số lượt đổi của MỘT user trên MỘT coupon, phục vụ per_user_redemption_limit.
CREATE INDEX idx_redemption_coupon_user ON coupon_redemptions USING btree (coupon_id, user_email);
--rollback DROP TABLE coupon_redemptions;

--changeset novaplay:003-outbox-events
-- Giống hệt bảng cùng tên bên auth-service. Trùng lặp là CHỦ ĐÍCH (xem "No Shared Library"
-- trong CLAUDE.md): hai service có DB riêng, deploy riêng, không dùng chung artifact nào.
CREATE TABLE outbox_events (
    id              uuid NOT NULL,
    topic           varchar(255) NOT NULL,
    event_key       varchar(255),
    payload         jsonb NOT NULL,
    status          varchar(20) DEFAULT 'PENDING' NOT NULL,
    attempts        integer DEFAULT 0 NOT NULL,
    created_at      timestamp with time zone DEFAULT now() NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    error_message   varchar(2000),
    CONSTRAINT outbox_events_pkey PRIMARY KEY (id)
);
-- PARTIAL index — đúng câu mà OutboxDao#claimBatch quét kèm FOR UPDATE SKIP LOCKED. Giữ nguyên
-- mệnh đề WHERE, bỏ nó đi là index không được dùng.
CREATE INDEX idx_outbox_due ON outbox_events USING btree (next_attempt_at) WHERE status = 'PENDING';
--rollback DROP TABLE outbox_events;

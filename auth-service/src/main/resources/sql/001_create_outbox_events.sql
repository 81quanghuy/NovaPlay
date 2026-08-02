-- Bảng outbox_events trước đây được Hibernate ddl-auto=update tự tạo từ JPA entity OutboxEvent.
-- Entity đó đã bị xoá (OutboxDao trong module utils dùng JdbcTemplate thuần, không qua JPA), nên
-- môi trường mới (docker-compose dựng lại, CI, máy dev mới) sẽ không còn cách nào tạo ra bảng này
-- nữa. Script này tái tạo đúng schema ban đầu (khớp OutboxEvent cũ trước khi bị xoá) để
-- 002_outbox_listen_notify.sql chạy tiếp phía sau như trước giờ, không đổi.
--
-- An toàn chạy nhiều lần (CREATE TABLE/INDEX IF NOT EXISTS) và an toàn chạy trên database đã có
-- sẵn bảng outbox_events (ví dụ: chạy tay trên database dev hiện tại) — sẽ là no-op ở đó.

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(255),
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    error_message VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events (status);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox_events (created_at);

-- Chuyển outbox từ mô hình quét định kỳ sang mô hình Postgres đẩy qua LISTEN/NOTIFY.
--
-- Chạy sau 001_create_outbox_events.sql. ddl-auto: update không tạo được partial index lẫn
-- trigger nên các thay đổi này phải khai báo tay ở đây — file này là nguồn sự thật cho schema
-- outbox và PHẢI chạy tay trước khi triển khai bản mới.

-- 1. Mốc backoff, đồng thời là điều kiện tranh quyền xử lý giữa các pod.
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;
UPDATE outbox_events SET next_attempt_at = created_at WHERE next_attempt_at IS NULL;
ALTER TABLE outbox_events ALTER COLUMN next_attempt_at SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN next_attempt_at SET DEFAULT now();

-- 2. Gửi thành công thì xoá row, nên không còn trạng thái SENT lẫn cột sent_at. Nhờ vậy bảng
--    luôn gần rỗng và không cần job dọn dẹp nào.
DELETE FROM outbox_events WHERE status = 'SENT';
ALTER TABLE outbox_events DROP COLUMN IF EXISTS sent_at;

-- 3. idx_outbox_status là index trên cột cardinality thấp, càng nhiều row càng vô dụng.
--    Partial index dưới đây chỉ chứa row PENDING — tức gần như luôn rỗng.
DROP INDEX IF EXISTS idx_outbox_status;
DROP INDEX IF EXISTS idx_outbox_created_at;
CREATE INDEX IF NOT EXISTS idx_outbox_due
    ON outbox_events (next_attempt_at) WHERE status = 'PENDING';

-- 4. pg_notify trong trigger là transactional: notification chỉ phát khi COMMIT. Đây chính là
--    thứ thay thế cho @TransactionalEventListener(AFTER_COMMIT) ở tầng ứng dụng, và nó phát cho
--    MỌI pod đang LISTEN chứ không riêng pod đã ghi row.
CREATE OR REPLACE FUNCTION outbox_notify() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('outbox_new', NEW.id::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS outbox_notify_trg ON outbox_events;
CREATE TRIGGER outbox_notify_trg
    AFTER INSERT ON outbox_events
    FOR EACH ROW EXECUTE FUNCTION outbox_notify();

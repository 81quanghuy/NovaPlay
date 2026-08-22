--liquibase formatted sql

-- Cơ chế đánh thức outbox relay. Tách khỏi 001 vì cần splitStatements:false — thân hàm plpgsql
-- chứa dấu ; và cặp $$, Liquibase mà cắt câu theo ; sẽ băm nát nó.
--
-- KHÔNG được bỏ qua phần này khi dựng môi trường mới: OutboxNotificationListener giữ một
-- connection Postgres riêng ở trạng thái LISTEN outbox_new, và nó chỉ được đánh thức bởi
-- pg_notify trong trigger dưới đây. Thiếu trigger thì email OTP/reset password sẽ KHÔNG bao giờ
-- được gửi cho tới khi có một lần catch-up (chỉ chạy lúc khởi động, lúc reconnect, hoặc gọi tay
-- qua OutboxCatchUpController) — triệu chứng là "đăng ký xong không nhận được mail" mà log
-- không hề có lỗi nào.

--changeset novaplay:008-outbox-notify-function splitStatements:false
CREATE OR REPLACE FUNCTION outbox_notify() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    PERFORM pg_notify('outbox_new', NEW.id::text);
    RETURN NEW;
END;
$$;
--rollback DROP FUNCTION IF EXISTS outbox_notify();

--changeset novaplay:009-outbox-notify-trigger
DROP TRIGGER IF EXISTS outbox_notify_trg ON outbox_events;
CREATE TRIGGER outbox_notify_trg
    AFTER INSERT ON outbox_events
    FOR EACH ROW
    EXECUTE FUNCTION outbox_notify();
--rollback DROP TRIGGER IF EXISTS outbox_notify_trg ON outbox_events;

--liquibase formatted sql

-- Hai role USER/ADMIN là REFERENCE DATA, không phải dữ liệu mẫu: constraint
-- roles_role_name_check ở 001 liệt kê đúng hai giá trị này, và luồng đăng ký gán role USER cho
-- mọi tài khoản mới. DB thiếu chúng thì đăng ký hỏng ngay từ người dùng đầu tiên.
--
-- Trước đây chúng chỉ được nạp bởi k8s/infra/postgres-seed.sql chạy tay ở dev, nghĩa là một DB
-- prod mới dựng sẽ KHÔNG có role nào và không có gì báo cho biết điều đó.
--
-- UUID cố định, không dùng gen_random_uuid(): role_id đi vào bảng user_roles nên phải giống nhau
-- giữa các môi trường, nếu không thì dump/restore hay so sánh dữ liệu giữa dev và prod đều lệch.
-- Hai giá trị này lấy đúng từ bản seed cũ để DB dev hiện có không bị lệch.

--changeset novaplay:010-seed-roles
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM roles
INSERT INTO roles (role_id, role_name, role_description, created_at, created_by, updated_at, updated_by)
VALUES
    ('966a4567-9ece-47d6-9445-f9bbda8870f4', 'USER',  'Default role for normal users',
     now(), 'system', now(), 'system'),
    ('92c3c2db-eaf8-403b-93c4-ee78bf82ba9f', 'ADMIN', 'Administrator role with full permissions',
     now(), 'system', now(), 'system');
--rollback DELETE FROM roles WHERE role_name IN ('USER', 'ADMIN');

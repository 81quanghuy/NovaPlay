-- USER Role
INSERT INTO roles (role_id, role_name, role_description, created_at, created_by, updated_at, updated_by)
VALUES (
    gen_random_uuid(),
    'USER',
    'Default role for normal users',
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    'system'
);

-- ADMIN Role
INSERT INTO roles (role_id, role_name, role_description, created_at, created_by, updated_at, updated_by)
VALUES (
    gen_random_uuid(),
    'ADMIN',
    'Administrator role with full permissions',
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    'system'
);
SELECT 'CREATE DATABASE promotion_service OWNER admin'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'promotion_service')\gexec

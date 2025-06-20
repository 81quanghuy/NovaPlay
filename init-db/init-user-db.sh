#!/bin/bash
set -e

echo "===> Creating database user_service..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE user_service;
EOSQL

echo "===> Done creating database user_service."

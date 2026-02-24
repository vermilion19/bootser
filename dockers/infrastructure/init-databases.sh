#!/bin/bash
set -e

# 웨이팅 서비스, 식당 서비스, 알림 서비스, 인증 서비스용 DB 생성
databases=("waiting_service_db" "restaurant_service_db" "noti_service_db" "auth_service_db")

for db in "${databases[@]}"; do
  echo "Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    SELECT 'CREATE DATABASE $db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
done

echo "All databases created successfully."

#!/bin/sh
set -e

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -v owner_password="$OMS_OWNER_PASSWORD" \
  -v app_password="$OMS_APP_PASSWORD" \
  -v db_name="$POSTGRES_DB" <<-'EOSQL'
    DO $$
    BEGIN
      IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'oms_owner') THEN
        CREATE ROLE oms_owner LOGIN;
      END IF;
      IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'oms_app') THEN
        CREATE ROLE oms_app LOGIN;
      END IF;
    END
    $$;

    ALTER ROLE oms_owner WITH LOGIN PASSWORD :'owner_password';
    ALTER ROLE oms_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS
      PASSWORD :'app_password';

    ALTER DATABASE :"db_name" OWNER TO oms_owner;
    GRANT USAGE, CREATE ON SCHEMA public TO oms_owner;
    GRANT USAGE ON SCHEMA public TO oms_app;
EOSQL

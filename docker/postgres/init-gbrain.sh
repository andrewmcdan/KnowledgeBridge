#!/bin/sh
set -eu

# Runs automatically on fresh volumes; can also provision an existing cluster.
# The application user/database must keep their distinct names.
if [ "$POSTGRES_USER" = gbrain ] || [ "$POSTGRES_DB" = gbrain ]; then
    echo 'POSTGRES_USER and POSTGRES_DB must differ from gbrain.' >&2
    exit 1
fi

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv gbrain_password GBRAIN_DB_PASSWORD
SELECT format('CREATE ROLE gbrain LOGIN PASSWORD %L', :'gbrain_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'gbrain')
\gexec
-- Upstream v24 requires BYPASSRLS; this role remains non-superuser.
ALTER ROLE gbrain BYPASSRLS;
SELECT 'CREATE DATABASE gbrain OWNER gbrain'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'gbrain')
\gexec
SQL

# pgvector requires a superuser for installation; gbrain itself does not.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname gbrain <<'SQL'
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Upstream migration v35 requires this administrator-created event trigger.
-- Pre-create it as upstream recommends for a non-superuser runtime role.
DO $setup$
BEGIN
    IF NOT EXISTS (
        SELECT FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE p.proname = 'auto_enable_rls' AND n.nspname = 'public'
    ) THEN
        EXECUTE $function$
            CREATE FUNCTION public.auto_enable_rls() RETURNS event_trigger
            LANGUAGE plpgsql AS $body$
            DECLARE obj record;
            BEGIN
                FOR obj IN SELECT * FROM pg_event_trigger_ddl_commands()
                    WHERE object_type = 'table' AND schema_name = 'public'
                LOOP
                    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', obj.object_identity);
                END LOOP;
            END;
            $body$
        $function$;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_event_trigger WHERE evtname = 'auto_rls_on_create_table') THEN
        CREATE EVENT TRIGGER auto_rls_on_create_table ON ddl_command_end
            WHEN TAG IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
            EXECUTE FUNCTION public.auto_enable_rls();
    END IF;
END
$setup$;
-- Later upstream migrations alter the function's search_path.
ALTER FUNCTION public.auto_enable_rls() OWNER TO gbrain;
SQL

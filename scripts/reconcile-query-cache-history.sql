-- Run only against a stopped application's database, after a verified pg_dump backup.
-- This repairs the known V7 -> V10 rename only; it does not run DDL or change application data.
-- -844063123 is Flyway's checksum of the unchanged query-cache SQL (verified by integration tests).
DO $$
BEGIN
    LOCK TABLE flyway_schema_history IN EXCLUSIVE MODE;

    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '10') THEN
        IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '7') OR NOT EXISTS (
            SELECT 1 FROM flyway_schema_history WHERE version = '10' AND success AND type = 'SQL'
                AND description = 'create query cache' AND script = 'V10__create_query_cache.sql'
                AND checksum = -844063123
        ) THEN
            RAISE EXCEPTION 'Unexpected cache migration history; no changes made';
        END IF;
        RAISE NOTICE 'Cache migration is already recorded as V10; no changes made';
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM flyway_schema_history WHERE version = '7' AND success AND type = 'SQL'
            AND description = 'create query cache' AND script = 'V7__create_query_cache.sql'
            AND checksum = -844063123
    ) OR NOT EXISTS (
        SELECT 1 FROM flyway_schema_history WHERE version = '8' AND success
            AND script = 'V8__add_video_ingestion_metadata.sql' AND checksum = -1991529897
    ) THEN
        RAISE EXCEPTION 'Legacy migration checksums/history do not match the supported V7 cache and V8 video history; no changes made';
    END IF;

    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'query_cache') <> 8
        OR (SELECT count(*) FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'query_cache' AND is_nullable = 'NO'
                AND (column_name, data_type) IN (
                    ('id', 'bigint'), ('exact_query_text', 'text'), ('query_mode', 'character varying'),
                    ('knowledge_revision', 'bigint'), ('response_json', 'jsonb'), ('hit_count', 'integer'),
                    ('created_at', 'timestamp with time zone'), ('last_accessed_at', 'timestamp with time zone')
                )) <> 8
        OR NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = current_schema()
            AND tablename = 'query_cache' AND indexname = 'uq_query_cache_key'
            AND indexdef LIKE 'CREATE UNIQUE INDEX % (query_mode, knowledge_revision, md5(exact_query_text))') THEN
        RAISE EXCEPTION 'Unexpected query_cache schema; no changes made';
    END IF;

    UPDATE flyway_schema_history SET version = '10', script = 'V10__create_query_cache.sql'
        WHERE version = '7';
END $$;

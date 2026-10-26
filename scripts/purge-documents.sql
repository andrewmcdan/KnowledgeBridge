-- Application content only. Accounts, Flyway history, and monotonically increasing IDs survive.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path = pg_catalog, public;
SET LOCAL lock_timeout = '5s';
SELECT set_config('knowledgebridge.purge_apply', :'apply', true) AS ignored \gset
DO $check$
DECLARE actual text[];
BEGIN
    SELECT array_agg(tablename::text ORDER BY tablename::text COLLATE "C") INTO actual
    FROM pg_tables WHERE schemaname = 'public';
    IF actual IS DISTINCT FROM ARRAY['app_user','detected_entity','entity_extraction','flyway_schema_history',
        'ingestion_attempt','knowledge_item','knowledge_item_entity','query_cache','query_usage','system_state'] THEN
        RAISE EXCEPTION 'Unsupported application schema. Review the purge table list before continuing.';
    END IF;
    IF current_setting('knowledgebridge.purge_apply') = 'true' AND EXISTS (
        SELECT FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND backend_type='client backend'
    ) THEN
        RAISE EXCEPTION 'Application database has other connections. Stop host backends, launchers and database clients first.';
    END IF;
END
$check$;
\if :apply
TRUNCATE public.knowledge_item_entity, public.entity_extraction, public.detected_entity,
    public.ingestion_attempt, public.knowledge_item, public.query_cache, public.query_usage RESTRICT;
UPDATE public.system_state SET knowledge_revision = knowledge_revision + 1 WHERE id = 1;
\endif
SELECT json_build_object(
    'documents', (SELECT count(*) FROM knowledge_item),
    'attempts', (SELECT count(*) FROM ingestion_attempt),
    'entities', (SELECT count(*) FROM detected_entity),
    'associations', (SELECT count(*) FROM knowledge_item_entity),
    'extractions', (SELECT count(*) FROM entity_extraction),
    'cachedAnswers', (SELECT count(*) FROM query_cache),
    'usageRecords', (SELECT count(*) FROM query_usage),
    'accounts', (SELECT count(*) FROM app_user),
    'sourcePaths', coalesce((SELECT json_agg(source_path) FROM knowledge_item WHERE source_path IS NOT NULL), '[]'::json)
);
COMMIT;

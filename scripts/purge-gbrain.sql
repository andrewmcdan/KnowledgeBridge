-- Offline reset for the dedicated, database-only gbrain pinned in docker/gbrain/Dockerfile.
-- Retains OAuth, source configuration, local writer identity and schema version; clears ALL content.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path = pg_catalog, public;
SET LOCAL lock_timeout = '5s';
SELECT set_config('knowledgebridge.purge_apply', :'apply', true) AS ignored \gset
DO $purge$
DECLARE
    retained text[] := ARRAY['access_tokens', 'config', 'oauth_clients', 'oauth_codes',
        'oauth_grant_audit', 'oauth_tokens', 'page_generation_clock', 'persistence_brain',
        'persistence_counters', 'persistence_host_bindings', 'persistence_local_writers', 'persistence_source_bindings',
        'persistence_worktrees', 'persistence_writer_protocols', 'sources'];
    content text[] := ARRAY['budget_ledger', 'budget_reservations', 'calibration_profiles', 'chat_usage_log',
        'code_edges_chunk', 'code_edges_symbol', 'code_traversal_cache', 'content_chunks',
        'context_volunteer_events', 'conversation_parser_llm_cache', 'decide_calibrations', 'decide_proposals',
        'decide_spend', 'decide_state', 'decide_sweep_deferred', 'decision_receipts',
        'dream_synthesis_completions', 'dream_verdicts', 'drift_decisions', 'entity_identities',
        'eval_candidates', 'eval_capture_failures', 'eval_contradictions_cache', 'eval_contradictions_runs',
        'eval_takes_quality_runs', 'extract_atoms_page_state', 'extract_atoms_transcript_state', 'extract_rollup_7d',
        'fact_relink_attempts', 'fact_withdrawals', 'facts', 'file_migration_ledger',
        'files', 'gbrain_cycle_locks', 'ingest_log', 'links',
        'loop_suppressions', 'mcp_request_log', 'mcp_spend_log', 'mcp_spend_reservations',
        'migration_impact_log', 'minion_attachments', 'minion_budget_log', 'minion_inbox',
        'minion_jobs', 'minion_lease_pressure_log', 'minion_self_fix_log', 'op_checkpoint_paths',
        'op_checkpoints', 'open_loops', 'page_aliases', 'page_projection_jobs',
        'page_versions', 'page_write_guards', 'pages', 'persistence_effects',
        'persistence_requests', 'persistence_topology_changes', 'persistence_worktree_refreshes', 'query_cache',
        'raw_data', 'search_telemetry', 'session_context_state', 'shared_skill_delivery_batches',
        'shared_skill_heads', 'shared_skill_members', 'shared_skill_packs', 'shared_skill_policies',
        'shared_skill_policy_audit', 'shared_skill_revision_leases', 'shared_skill_revisions', 'shared_skill_state',
        'slug_aliases', 'source_ingestion_receipts', 'subagent_messages', 'subagent_rate_leases',
        'subagent_tool_executions', 'synthesis_evidence', 'tags', 'take_domain_assignments',
        'take_grade_cache', 'take_nudge_log', 'take_proposals', 'takes',
        'think_ab_results', 'timeline_entries'];
    actual text[];
    target text;
    rows_left bigint;
BEGIN
    SELECT array_agg(tablename::text ORDER BY tablename::text COLLATE "C") INTO actual FROM pg_tables WHERE schemaname='public';
    IF actual IS DISTINCT FROM ARRAY(SELECT name FROM unnest(retained || content) name ORDER BY name COLLATE "C")
        OR (SELECT value FROM config WHERE key='version') IS DISTINCT FROM '196' THEN
        RAISE EXCEPTION 'Unsupported gbrain schema. Purge supports pinned migration 196 only; review before upgrading.';
    END IF;
    IF EXISTS (SELECT FROM sources WHERE id NOT IN ('default','knowledgebridge')) THEN
        RAISE EXCEPTION 'Refusing to purge shared sources; a dedicated KnowledgeBridge brain is required.';
    END IF;
    IF EXISTS (SELECT FROM sources WHERE nullif(local_path,'') IS NOT NULL AND local_path <> '/data/sources/knowledgebridge') THEN
        RAISE EXCEPTION 'Refusing source paths outside the database-only KnowledgeBridge layout.';
    END IF;
    IF EXISTS (SELECT FROM persistence_source_bindings) OR EXISTS (SELECT FROM persistence_worktrees) THEN
        RAISE EXCEPTION 'Refusing to purge canonical source/worktree bindings.';
    END IF;
    IF EXISTS (SELECT FROM files) THEN
        RAISE EXCEPTION 'Engine-managed attachments require separate object-storage cleanup. Backend uploads are supported; refusing a partial purge.';
    END IF;
    IF current_setting('knowledgebridge.purge_apply') = 'true' THEN
        IF EXISTS (SELECT FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND backend_type='client backend') THEN
            RAISE EXCEPTION 'gbrain database has other connections. Stop engine workers and database clients first.';
        END IF;
        SELECT string_agg(format('public.%I',name), ', ') INTO target FROM unnest(content) name;
        EXECUTE 'TRUNCATE ' || target || ' RESTRICT';
        UPDATE page_generation_clock SET value=value+1;
        UPDATE persistence_counters SET outstanding_count=0, intent_bytes=0, terminal_bytes=0, recovery_bytes=0;
        -- Offline maintenance checkpoint update, scoped to the two sources validated above.
        PERFORM set_config('gbrain.write_sources', '["default","knowledgebridge"]', true);
        UPDATE sources SET last_commit=NULL, last_sync_at=NULL, newest_content_at=NULL,
            upstream_checked_at=NULL, upstream_commit=NULL, upstream_behind=NULL;
        FOREACH target IN ARRAY content LOOP
            EXECUTE format('SELECT count(*) FROM public.%I',target) INTO rows_left;
            IF rows_left <> 0 THEN RAISE EXCEPTION 'Engine content verification failed'; END IF;
        END LOOP;
    END IF;
END
$purge$;
SELECT json_build_object(
    'pages', (SELECT count(*) FROM pages),
    'chunks', (SELECT count(*) FROM content_chunks),
    'versions', (SELECT count(*) FROM page_versions),
    'jobs', (SELECT count(*) FROM minion_jobs),
    'writeReceipts', (SELECT count(*) FROM persistence_requests),
    'oauthClients', (SELECT count(*) FROM oauth_clients)
);
COMMIT;

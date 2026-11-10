-- Links a knowledge item to the external record it was synced from. Null for uploads and manual notes.
ALTER TABLE knowledge_item
    ADD COLUMN external_source VARCHAR(32),
    ADD COLUMN external_key VARCHAR(64),
    ADD COLUMN external_url VARCHAR(512),
    ADD COLUMN external_updated_at TIMESTAMPTZ;

-- PostgreSQL treats NULLs as distinct, so items without an external source are unaffected.
CREATE UNIQUE INDEX uq_knowledge_item_external ON knowledge_item (external_source, external_key);

CREATE TABLE jira_sync_run (
    id BIGSERIAL PRIMARY KEY,
    project_key VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    created_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    unchanged_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    triggered_by BIGINT NOT NULL REFERENCES app_user(id)
);

-- Enforces at most one active sync per project, even if two requests race.
CREATE UNIQUE INDEX uq_jira_sync_run_active_project ON jira_sync_run (project_key) WHERE status = 'RUNNING';

-- Single-row table holding the knowledge_revision counter. Bumped by IngestionService whenever an
-- item's content successfully changes; the query cache (not built yet) will key cached answers on
-- this value so a knowledge write invalidates every answer built on the old content.
CREATE TABLE system_state (
    id                 SMALLINT PRIMARY KEY DEFAULT 1,
    knowledge_revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT system_state_singleton CHECK (id = 1)
);

INSERT INTO system_state (id, knowledge_revision) VALUES (1, 0);

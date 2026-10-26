-- Naive exact-match response cache for Synthesis mode. A cached answer is only reused while
-- knowledge_revision (V5's system_state counter) is unchanged, so any knowledge write makes every
-- older entry stale without deleting it. Stale rows stay until an admin clears the cache.
CREATE TABLE query_cache (
    id                 BIGSERIAL PRIMARY KEY,
    exact_query_text   TEXT NOT NULL,
    query_mode         VARCHAR(20) NOT NULL CHECK (query_mode IN ('SEARCH', 'SYNTHESIS')),
    knowledge_revision BIGINT NOT NULL,
    response_json      JSONB NOT NULL,
    hit_count          INTEGER NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_accessed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The cache key. Hashing the text keeps long questions under PostgreSQL's B-tree entry size limit
-- (about 2.7 KB); lookups must filter on md5(exact_query_text) to use this index, then compare the
-- full text. Unique so two identical requests racing on a miss can't store duplicate entries.
CREATE UNIQUE INDEX uq_query_cache_key
    ON query_cache (query_mode, knowledge_revision, md5(exact_query_text));

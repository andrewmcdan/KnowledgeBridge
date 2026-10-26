-- Content timestamps stay stable across ingestion retries and advance only when the text changes.
ALTER TABLE knowledge_item ADD COLUMN content_updated_at TIMESTAMPTZ;
UPDATE knowledge_item SET content_updated_at = created_at;
ALTER TABLE knowledge_item ALTER COLUMN content_updated_at SET NOT NULL;
ALTER TABLE knowledge_item ALTER COLUMN content_updated_at SET DEFAULT now();

-- Successful query requests only. Token counts are reported by the engine or estimated, never billing records.
CREATE TABLE query_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    query_mode VARCHAR(20) NOT NULL CHECK (query_mode IN ('SEARCH', 'SYNTHESIS')),
    cache_hit BOOLEAN NOT NULL,
    input_tokens BIGINT NOT NULL CHECK (input_tokens >= 0),
    output_tokens BIGINT NOT NULL CHECK (output_tokens >= 0),
    estimated BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_query_usage_user ON query_usage (user_id, created_at);

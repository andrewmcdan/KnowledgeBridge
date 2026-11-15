ALTER TABLE query_usage ADD COLUMN provider_model VARCHAR(255);
ALTER TABLE query_usage ADD COLUMN estimated_cost_usd NUMERIC(18, 8)
    CHECK (estimated_cost_usd IS NULL OR estimated_cost_usd >= 0);

CREATE INDEX idx_query_usage_created_at ON query_usage (created_at);

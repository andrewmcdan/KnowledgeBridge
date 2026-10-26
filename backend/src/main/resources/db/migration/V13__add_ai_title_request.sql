-- Persist naming intent so video jobs and ingestion retries retain the user's title guidance.
ALTER TABLE knowledge_item ADD COLUMN ai_title_requested BOOLEAN NOT NULL DEFAULT false;

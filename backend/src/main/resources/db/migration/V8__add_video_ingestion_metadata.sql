ALTER TABLE knowledge_item
    ADD COLUMN source_kind VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN ingestion_stage VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    ADD COLUMN original_filename VARCHAR(255),
    ADD COLUMN source_content_type VARCHAR(255),
    ADD COLUMN source_sha256 VARCHAR(64),
    ADD COLUMN source_path VARCHAR(1024),
    ADD COLUMN media_duration_ms BIGINT,
    ADD COLUMN transcription_model VARCHAR(255),
    ADD COLUMN transcription_language VARCHAR(64),
    ADD COLUMN transcription_segment_count INTEGER,
    ADD COLUMN transcribed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_knowledge_item_ingestion_status
    ON knowledge_item (status, id);

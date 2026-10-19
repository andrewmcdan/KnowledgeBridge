-- The engine owns entity pages; this projection maps its detections to application documents.
CREATE TABLE detected_entity (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(512) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('PERSON', 'COMPANY', 'PROJECT', 'TEAM')),
    summary TEXT NOT NULL
);

CREATE TABLE entity_extraction (
    knowledge_item_id BIGINT PRIMARY KEY REFERENCES knowledge_item(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE knowledge_item_entity (
    knowledge_item_id BIGINT NOT NULL REFERENCES knowledge_item(id) ON DELETE CASCADE,
    entity_id BIGINT NOT NULL REFERENCES detected_entity(id) ON DELETE CASCADE,
    PRIMARY KEY (knowledge_item_id, entity_id)
);
CREATE INDEX idx_knowledge_item_entity_entity ON knowledge_item_entity(entity_id, knowledge_item_id);

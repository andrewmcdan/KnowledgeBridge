CREATE TABLE knowledge_item (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    owner_id BIGINT NOT NULL REFERENCES  app_user(id),
    status VARCHAR(20) NOT NULL
                            CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    character_count INTEGER NOT NULL,
    external_engine_id VARCHAR(255), -- nullable because its only present if the ingestion didnt fail inside of gbrain (want a row regardless for retries).
    soft_deleted_at TIMESTAMPTZ, -- for later implementations
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ingestion_attempt (
    id BIGSERIAL PRIMARY KEY ,
    knowledge_item_id BIGINT NOT NULL REFERENCES knowledge_item(id),
    attempt_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    UNIQUE (knowledge_item_id, attempt_number)
);

CREATE INDEX idx_ingestion_attempt_knowledge_item ON ingestion_attempt(knowledge_item_id);

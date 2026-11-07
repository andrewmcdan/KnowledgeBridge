ALTER TABLE knowledge_item ADD COLUMN lifecycle_state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE knowledge_item ADD COLUMN trashed_from VARCHAR(16);
UPDATE knowledge_item SET lifecycle_state = 'TRASH', trashed_from = 'ACTIVE' WHERE soft_deleted_at IS NOT NULL;
ALTER TABLE knowledge_item ADD CONSTRAINT knowledge_item_lifecycle_check CHECK (
    lifecycle_state IN ('ACTIVE', 'INACTIVE', 'TRASH') AND
    ((lifecycle_state = 'TRASH' AND trashed_from IS NOT NULL AND trashed_from IN ('ACTIVE', 'INACTIVE') AND soft_deleted_at IS NOT NULL)
     OR (lifecycle_state <> 'TRASH' AND trashed_from IS NULL AND soft_deleted_at IS NULL))
);

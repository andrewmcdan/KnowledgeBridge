-- Per-item version, separate from the global knowledge_revision in V5. gbrain needs this on every
-- write (GbrainDocument.revision) so it can tell a stale write from a current one. Starts at 1 since
-- the first successful ingestion is revision 1; a future edit endpoint would bump it.
ALTER TABLE knowledge_item ADD COLUMN revision BIGINT NOT NULL DEFAULT 1;

-- Needed so retry can resubmit the same content, and so a future GET /api/knowledge-items/{id} can show
-- the full text. We previously only stored character_count, not the text itself.
ALTER TABLE knowledge_item ADD COLUMN body_text TEXT NOT NULL DEFAULT '';

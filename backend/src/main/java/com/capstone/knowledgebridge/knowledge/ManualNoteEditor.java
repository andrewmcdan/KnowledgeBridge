package com.capstone.knowledgebridge.knowledge;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/** Claims a note before network I/O; stale forms and overlapping edits cannot overwrite a newer revision. */
@Service
public class ManualNoteEditor {

	private final JdbcTemplate jdbc;
	private final KnowledgeItemRepository items;
	private final KnowledgeRevisionService revisions;
	private final EntityManager entities;

	public ManualNoteEditor(JdbcTemplate jdbc, KnowledgeItemRepository items, KnowledgeRevisionService revisions,
			EntityManager entities) {
		this.jdbc = jdbc;
		this.items = items;
		this.revisions = revisions;
		this.entities = entities;
	}

	@Transactional
	public Optional<KnowledgeItem> prepareUpdate(long id, String title, String type, String text, long revision) {
		int changed = jdbc.update("""
				UPDATE knowledge_item SET title = ?, type = ?, body_text = ?, character_count = ?,
				    revision = revision + 1, status = 'PROCESSING', ingestion_stage = 'INDEXING', updated_at = now(),
				    content_updated_at = now()
				WHERE id = ? AND revision = ? AND source_kind = 'MANUAL' AND status IN ('COMPLETED', 'FAILED')
				""", title.trim(), type.trim(), text, text.length(), id, revision);
		if (changed == 0) {
			return Optional.empty();
		}
		// Invalidate old answers even if reindexing later fails. Commit both changes before contacting gbrain.
		revisions.bump();
		// JDBC changed the row directly; discard any entity loaded earlier by this request.
		entities.clear();
		return items.findById(id);
	}

	@Transactional
	public Optional<KnowledgeItem> prepareRetry(long id, long revision) {
		int changed = jdbc.update("""
				UPDATE knowledge_item SET status = 'PROCESSING', ingestion_stage = 'INDEXING', updated_at = now()
				WHERE id = ? AND revision = ? AND source_kind = 'MANUAL' AND status = 'FAILED'
				""", id, revision);
		if (changed == 0) {
			return Optional.empty();
		}
		entities.clear();
		return items.findById(id);
	}
}

package com.capstone.knowledgebridge.entities;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.gbrain.model.GbrainEntity;
import com.capstone.knowledgebridge.ingestion.GbrainDocumentMapper;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;

/** Keeps engine detections linked to the exact indexed revision, independently of ingestion success. */
@Service
public class EntityService {

	public record EntitySummary(long id, String name, String type, String summary, long documentCount,
			boolean verified) {
	}

	public record RelatedDocument(long id, String title, String type) {
	}

	public record DocumentEntities(String status, boolean truncated, String errorCode, List<EntitySummary> entities) {
	}

	private static final Logger log = LoggerFactory.getLogger(EntityService.class);

	private static final String VISIBLE = """
			 FROM detected_entity d
			 JOIN knowledge_item_entity m ON m.entity_id = d.id
			 JOIN knowledge_item k ON k.id = m.knowledge_item_id
			 JOIN entity_extraction x ON x.knowledge_item_id = k.id AND x.revision = k.revision
			 WHERE k.status = 'COMPLETED' AND x.status = 'COMPLETED'
			""";

	private static final String SELECT = "SELECT d.id, d.name, d.type, d.summary, COUNT(*) AS document_count";

	private static final String GROUP = " GROUP BY d.id ORDER BY COUNT(*) DESC, lower(d.name), d.id LIMIT ?";

	private static final RowMapper<EntitySummary> ENTITY_ROW = (rs, row) -> new EntitySummary(rs.getLong("id"),
			rs.getString("name"), rs.getString("type"), rs.getString("summary"), rs.getLong("document_count"), false);

	private final GbrainClient gbrainClient;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	public EntityService(GbrainClient gbrainClient, JdbcTemplate jdbc, TransactionTemplate transaction) {
		this.gbrainClient = gbrainClient;
		this.jdbc = jdbc;
		this.transaction = transaction;
	}

	public void extract(KnowledgeItem item) {
		try {
			var result = gbrainClient.extractEntities(GbrainDocumentMapper.toDocument(item));
			transaction.executeWithoutResult(status -> {
				jdbc.update("DELETE FROM knowledge_item_entity WHERE knowledge_item_id = ?", item.getId());
				for (GbrainEntity entity : result.entities()) {
					Long entityId = jdbc.queryForObject("""
							INSERT INTO detected_entity (external_id, name, type, summary) VALUES (?, ?, ?, ?)
							ON CONFLICT (external_id) DO UPDATE SET name = EXCLUDED.name,
							type = EXCLUDED.type, summary = EXCLUDED.summary RETURNING id
							""", Long.class, entity.externalId(), entity.name(), entity.type(), entity.summary());
					jdbc.update("INSERT INTO knowledge_item_entity VALUES (?, ?) ON CONFLICT DO NOTHING", item.getId(),
							entityId);
				}
				jdbc.update("""
						INSERT INTO entity_extraction (knowledge_item_id, revision, status, truncated)
						VALUES (?, ?, 'COMPLETED', ?) ON CONFLICT (knowledge_item_id) DO UPDATE
						SET revision = EXCLUDED.revision, status = 'COMPLETED', truncated = EXCLUDED.truncated,
						error_code = NULL, updated_at = now()
						""", item.getId(), item.getRevision(), result.truncated());
			});
		} catch (GbrainException exception) {
			jdbc.update("""
					INSERT INTO entity_extraction (knowledge_item_id, revision, status, error_code)
					VALUES (?, ?, 'FAILED', ?) ON CONFLICT (knowledge_item_id) DO UPDATE
					SET revision = EXCLUDED.revision, status = 'FAILED', error_code = EXCLUDED.error_code,
						truncated = FALSE, updated_at = now()
					""", item.getId(), item.getRevision(), exception.code().name());
			log.warn("Entity extraction failed for knowledge item {}: {}", item.getId(), exception.code());
		}
	}

	public List<EntitySummary> list(String name, String type, int limit) {
		return jdbc.query(
				SELECT + VISIBLE + " AND position(lower(?) in lower(d.name)) > 0 AND (? = '' OR d.type = ?)" + GROUP,
				ENTITY_ROW,
				name, type, type, limit);
	}

	public Optional<EntitySummary> find(long id) {
		return jdbc.query(SELECT + VISIBLE + " AND d.id = ?" + GROUP,
				ENTITY_ROW,
				id, 1).stream().findFirst();
	}

	public List<RelatedDocument> documents(long id, int limit) {
		return jdbc.query(
				"SELECT k.id, k.title, k.type" + VISIBLE
						+ " AND d.id = ? ORDER BY k.created_at DESC, k.id DESC LIMIT ?",
				(rs, row) -> new RelatedDocument(rs.getLong("id"), rs.getString("title"), rs.getString("type")), id,
				limit);
	}

	public DocumentEntities forDocument(KnowledgeItem item, int limit) {
		List<EntitySummary> entities = jdbc.query(SELECT + VISIBLE + " AND k.id = ?" + GROUP,
				ENTITY_ROW,
				item.getId(), limit);
		return jdbc.query(
				"SELECT status, truncated, error_code FROM entity_extraction WHERE knowledge_item_id = ? AND revision = ?",
				(rs, row) -> new DocumentEntities(rs.getString("status"), rs.getBoolean("truncated"),
						rs.getString("error_code"), entities),
				item.getId(), item.getRevision()).stream().findFirst()
				.orElse(new DocumentEntities("NOT_EXTRACTED", false, null, List.of()));
	}
}

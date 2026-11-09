package com.capstone.knowledgebridge.knowledge;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.ingestion.GbrainDocumentMapper;
import com.capstone.knowledgebridge.video.VideoStorageService;

/** Serializes lifecycle changes against item writes; success requires engine exclusion/reindexing first. */
@Service
public class KnowledgeLifecycleService {
	public enum Action {
		DEACTIVATE, ACTIVATE, TRASH, RESTORE
	}
	private static final Logger log = LoggerFactory.getLogger(KnowledgeLifecycleService.class);
	private final KnowledgeItemRepository items;
	private final GbrainClient engine;
	private final KnowledgeRevisionService revisions;
	private final JdbcTemplate jdbc;
	private final VideoStorageService storage;

	public KnowledgeLifecycleService(KnowledgeItemRepository items, GbrainClient engine,
			KnowledgeRevisionService revisions, JdbcTemplate jdbc, VideoStorageService storage) {
		this.items = items;
		this.engine = engine;
		this.revisions = revisions;
		this.jdbc = jdbc;
		this.storage = storage;
	}

	@Transactional
	public KnowledgeItem change(long id, Action action) {
		KnowledgeItem item = locked(id);
		KnowledgeLifecycle current = item.getLifecycleState();
		KnowledgeLifecycle target = switch (action) {
			case DEACTIVATE -> {
				require(current == KnowledgeLifecycle.ACTIVE);
				yield KnowledgeLifecycle.INACTIVE;
			}
			case ACTIVATE -> {
				require(current == KnowledgeLifecycle.INACTIVE);
				yield KnowledgeLifecycle.ACTIVE;
			}
			case TRASH -> {
				require(current != KnowledgeLifecycle.TRASH);
				yield KnowledgeLifecycle.TRASH;
			}
			case RESTORE -> {
				require(current == KnowledgeLifecycle.TRASH);
				yield item.getTrashedFrom();
			}
		};
		if (target == KnowledgeLifecycle.ACTIVE && item.getStatus() == KnowledgeItemStatus.COMPLETED) {
			// Reindex from our retained body even if gbrain's recoverable copy has expired.
			engine.upsertDocument(GbrainDocumentMapper.toDocument(item));
		} else {
			engine.deleteDocument(Long.toString(id));
		}
		item.changeLifecycle(target);
		revisions.bump();
		log.info("Knowledge item {} lifecycle changed from {} to {}", id, current, target);
		return items.save(item);
	}

	@Transactional
	public void permanentlyDelete(long id) {
		KnowledgeItem item = locked(id);
		require(item.getLifecycleState() == KnowledgeLifecycle.TRASH);
		engine.deleteDocument(Long.toString(id));
		if (item.getSourcePath() != null) {
			storage.deleteRetainedSource(Path.of(item.getSourcePath()));
		}
		// Remove only orphaned entities associated with this item; preserve shared entity records.
		jdbc.update(
				"""
						DELETE FROM detected_entity d WHERE d.id IN
						(SELECT entity_id FROM knowledge_item_entity WHERE knowledge_item_id = ?)
						AND NOT EXISTS (SELECT 1 FROM knowledge_item_entity m WHERE m.entity_id = d.id AND m.knowledge_item_id <> ?)
						""",
				id, id);
		jdbc.update("DELETE FROM ingestion_attempt WHERE knowledge_item_id = ?", id);
		items.delete(item);
		items.flush();
		// Old cached answers may contain document text; physically remove these copies too.
		jdbc.update("DELETE FROM query_cache");
		revisions.bump();
		log.info("Permanently removed knowledge item {} from application storage; engine retention applies", id);
	}

	private KnowledgeItem locked(long id) {
		KnowledgeItem item = items.findForLifecycle(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		require(item.getStatus() != KnowledgeItemStatus.PENDING && item.getStatus() != KnowledgeItemStatus.PROCESSING);
		return item;
	}

	private static void require(boolean allowed) {
		if (!allowed)
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"The document changed or ingestion is still running. Refresh the list before trying again.");
	}
}

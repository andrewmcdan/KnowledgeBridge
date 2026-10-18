package com.capstone.knowledgebridge.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.ingestion.IngestionAttempt;
import com.capstone.knowledgebridge.ingestion.IngestionAttemptRepository;
import com.capstone.knowledgebridge.ingestion.IngestionService;
import com.capstone.knowledgebridge.video.StoredVideo;
import com.capstone.knowledgebridge.video.VideoIngestionDispatcher;

/**
 * Creates knowledge items and hands them to IngestionService. Both the manual-entry and upload endpoints funnel through
 * createAndIngest, so there's exactly one place deciding how a submission becomes a KnowledgeItem row plus a gbrain
 * write.
 */
@Service
public class KnowledgeItemService {

	private final KnowledgeItemRepository knowledgeItemRepository;

	private final IngestionAttemptRepository ingestionAttemptRepository;

	private final IngestionService ingestionService;

	private final VideoIngestionDispatcher videoIngestionDispatcher;

	public KnowledgeItemService(KnowledgeItemRepository knowledgeItemRepository,
			IngestionAttemptRepository ingestionAttemptRepository, IngestionService ingestionService,
			VideoIngestionDispatcher videoIngestionDispatcher) {
		this.knowledgeItemRepository = knowledgeItemRepository;
		this.ingestionAttemptRepository = ingestionAttemptRepository;
		this.ingestionService = ingestionService;
		this.videoIngestionDispatcher = videoIngestionDispatcher;
	}

	public KnowledgeItem createVideoAndIngest(String title, String type, Long ownerId, StoredVideo video) {
		KnowledgeItem item = KnowledgeItem.video(title.trim(), type.trim(), ownerId, video.originalFilename(),
				video.contentType(), video.sha256(), video.path().toString());
		item = knowledgeItemRepository.save(item);
		videoIngestionDispatcher.enqueue(item.getId());
		return item;
	}

	public KnowledgeItem createAndIngest(String title, String type, String bodyText, Long ownerId) {
		return createAndIngest(title, type, bodyText, ownerId, KnowledgeSourceKind.MANUAL);
	}

	public KnowledgeItem createMarkdownAndIngest(String title, String type, String bodyText, Long ownerId) {
		return createAndIngest(title, type, bodyText, ownerId, KnowledgeSourceKind.MARKDOWN);
	}

	private KnowledgeItem createAndIngest(String title, String type, String bodyText, Long ownerId,
			KnowledgeSourceKind sourceKind) {
		KnowledgeItem item = new KnowledgeItem(title.trim(), type.trim(), ownerId, bodyText, sourceKind);
		// Save first so the item has an id - IngestionService/GbrainDocumentMapper need it (it becomes
		// the gbrain item key) and ingestion_attempt rows need it as a foreign key.
		item = knowledgeItemRepository.save(item);
		ingestionService.ingest(item);
		return knowledgeItemRepository.save(item);
	}

	/** Newest first, so a freshly submitted item shows up at the top of the admin list. */
	public List<KnowledgeItem> findAll() {
		return knowledgeItemRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
	}

	public Optional<KnowledgeItem> findById(Long id) {
		return knowledgeItemRepository.findById(id);
	}

	/** Latest ingestion attempt keyed by item id. An item with no attempts yet is simply absent from the map. */
	public Map<Long, IngestionAttempt> findLatestAttempts(Collection<Long> itemIds) {
		// Skip the query entirely for an empty list - nothing to look up, and IN () isn't valid SQL.
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		return ingestionAttemptRepository.findLatestByKnowledgeItemIdIn(itemIds)
				.stream()
				.collect(Collectors.toMap(IngestionAttempt::getKnowledgeItemId, Function.identity()));
	}

	/** Caller (the controller) is responsible for only calling this on a FAILED item. */
	public KnowledgeItem retry(KnowledgeItem item) {
		if (item.getSourceKind() == KnowledgeSourceKind.VIDEO) {
			item.markQueued();
			item = knowledgeItemRepository.save(item);
			videoIngestionDispatcher.enqueue(item.getId());
			return item;
		}
		ingestionService.ingest(item);
		return knowledgeItemRepository.save(item);
	}

}

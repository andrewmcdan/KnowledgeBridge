package com.capstone.knowledgebridge.knowledge;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.ingestion.IngestionService;

/**
 * Creates knowledge items and hands them to IngestionService. Both the manual-entry and upload endpoints funnel through
 * createAndIngest, so there's exactly one place deciding how a submission becomes a KnowledgeItem row plus a gbrain
 * write.
 */
@Service
public class KnowledgeItemService {

	private final KnowledgeItemRepository knowledgeItemRepository;

	private final IngestionService ingestionService;

	public KnowledgeItemService(KnowledgeItemRepository knowledgeItemRepository, IngestionService ingestionService) {
		this.knowledgeItemRepository = knowledgeItemRepository;
		this.ingestionService = ingestionService;
	}

	public KnowledgeItem createAndIngest(String title, String type, String bodyText, Long ownerId) {
		KnowledgeItem item = new KnowledgeItem(title.trim(), type.trim(), ownerId, bodyText);
		// Save first so the item has an id - IngestionService/GbrainDocumentMapper need it (it becomes
		// the gbrain item key) and ingestion_attempt rows need it as a foreign key.
		item = knowledgeItemRepository.save(item);
		ingestionService.ingest(item);
		return knowledgeItemRepository.save(item);
	}

	public Optional<KnowledgeItem> findById(Long id) {
		return knowledgeItemRepository.findById(id);
	}

	/** Caller (the controller) is responsible for only calling this on a FAILED item. */
	public KnowledgeItem retry(KnowledgeItem item) {
		ingestionService.ingest(item);
		return knowledgeItemRepository.save(item);
	}

}

package com.capstone.knowledgebridge.ingestion;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeRevisionService;

/**
 * Pushes a knowledge item's content to gbrain and records what happened. Synchronous for MVP - this method is
 * deliberately not wrapped in a Spring transaction, so we're not holding a DB connection open across the network call.
 * The caller (KnowledgeItemService) is expected to have already saved `item` (so it has an id) before calling ingest,
 * and to save it again afterward since this only mutates the in-memory entity.
 */
@Service
public class IngestionService {

	// Longest string ingestion_attempt.error_message (VARCHAR(1000)) will hold.
	private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private final GbrainClient gbrainClient;

	private final IngestionAttemptRepository ingestionAttemptRepository;

	private final KnowledgeRevisionService knowledgeRevisionService;

	public IngestionService(GbrainClient gbrainClient, IngestionAttemptRepository ingestionAttemptRepository,
			KnowledgeRevisionService knowledgeRevisionService) {
		this.gbrainClient = gbrainClient;
		this.ingestionAttemptRepository = ingestionAttemptRepository;
		this.knowledgeRevisionService = knowledgeRevisionService;
	}

	/**
	 * True when gbrain would reject this body for size. Lets the endpoints answer 413 up front instead of saving an
	 * item that can only ever fail. Mirrors GbrainDocument's normalization (strip trailing whitespace, append one
	 * newline); its CRLF and byte-order-mark handling only shrink the body, so this never lets an oversized body
	 * through.
	 */
	public static boolean exceedsBodyLimit(String body) {
		return body.stripTrailing().getBytes(StandardCharsets.UTF_8).length + 1 > GbrainDocument.MAX_BODY_BYTES;
	}

	/**
	 * Moves item from PENDING (or FAILED, on retry) to PROCESSING, submits its body to gbrain, and leaves it COMPLETED
	 * or FAILED.
	 */
	public void ingest(KnowledgeItem item) {
		item.markProcessing();
		int attemptNumber = (int) ingestionAttemptRepository.countByKnowledgeItemId(item.getId()) + 1;
		Instant startedAt = Instant.now();

		try {
			// GbrainDocument's own constructor validates (body size, title length, control characters,
			// ...) and throws GbrainException on a bad value, same as upsertDocument failing - both need
			// to land in the same catch block so a too-large body fails the attempt cleanly instead of
			// escaping as an uncaught exception.
			GbrainDocument document = GbrainDocumentMapper.toDocument(item);
			GbrainWriteResult result = gbrainClient.upsertDocument(document);
			item.markCompleted(result.externalId());
			ingestionAttemptRepository.save(IngestionAttempt.succeeded(item.getId(), attemptNumber, startedAt));
			long revision = knowledgeRevisionService.bump();
			log.info("Ingested knowledge item {} into gbrain as '{}' ({}, {} chunks, knowledge_revision={})",
					item.getId(), result.externalId(), result.status(), result.chunks(), revision);
		} catch (GbrainException ex) {
			item.markFailed();
			String errorMessage = ex.upstreamCode()
					.map(upstream -> ex.getMessage() + " (gbrain: " + upstream + ")")
					.orElse(ex.getMessage());
			ingestionAttemptRepository
					.save(IngestionAttempt.failed(item.getId(), attemptNumber, startedAt, ex.code().name(),
							truncate(errorMessage)));
			log.warn("Ingestion failed for knowledge item {} ({}): {}", item.getId(), ex.code(), ex.getMessage());
		}
	}

	private static String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= MAX_ERROR_MESSAGE_LENGTH ? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
	}

}

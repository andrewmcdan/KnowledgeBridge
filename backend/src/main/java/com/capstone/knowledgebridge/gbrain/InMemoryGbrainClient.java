package com.capstone.knowledgebridge.gbrain;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.capstone.knowledgebridge.gbrain.model.GbrainDeleteResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainRestoreResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainRetrieval;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainStoredDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;

/**
 * Deterministic stand-in for local development and tests when gbrain is unavailable. It mirrors the pinned server's
 * page semantics: identical content is {@code UNCHANGED}, any write revives a soft-deleted page, and delete/restore
 * report already-applied states. Nothing is indexed or searchable, and contents are lost on restart: search always
 * returns no hits with {@code vectorEnabled=false}, and synthesis fails as {@link GbrainErrorCode#UNAVAILABLE} rather
 * than inventing an answer.
 */
class InMemoryGbrainClient implements GbrainClient {

	private final Map<String, GbrainStoredDocument> pages = new ConcurrentHashMap<>();

	private final Clock clock;

	InMemoryGbrainClient(Clock clock) {
		this.clock = clock;
	}

	@Override
	public synchronized GbrainWriteResult upsertDocument(GbrainDocument document) {
		String slug = GbrainPages.slugFor(document.itemKey());
		GbrainStoredDocument previous = pages.put(slug, new GbrainStoredDocument(slug, document.title(),
				document.documentType(), document.revision(), document.contentDigest(), null));
		boolean unchanged = previous != null && !previous.deleted()
				&& document.contentDigest().equals(previous.contentDigest());
		return unchanged
				? new GbrainWriteResult(slug, GbrainWriteResult.Status.UNCHANGED, 0)
				: new GbrainWriteResult(slug, GbrainWriteResult.Status.WRITTEN, 1);
	}

	@Override
	public Optional<GbrainStoredDocument> getDocument(String itemKey) {
		return Optional.ofNullable(pages.get(GbrainPages.slugFor(itemKey)));
	}

	@Override
	public synchronized GbrainDeleteResult deleteDocument(String itemKey) {
		String slug = GbrainPages.slugFor(itemKey);
		GbrainStoredDocument page = pages.get(slug);
		if (page == null) {
			return GbrainDeleteResult.NOT_FOUND;
		}
		if (page.deleted()) {
			return GbrainDeleteResult.ALREADY_DELETED;
		}
		pages.put(slug, withDeletedAt(page, clock.instant()));
		return GbrainDeleteResult.DELETED;
	}

	@Override
	public synchronized GbrainRestoreResult restoreDocument(String itemKey) {
		String slug = GbrainPages.slugFor(itemKey);
		GbrainStoredDocument page = pages.get(slug);
		if (page == null) {
			return GbrainRestoreResult.NOT_FOUND;
		}
		if (!page.deleted()) {
			return GbrainRestoreResult.ALREADY_ACTIVE;
		}
		pages.put(slug, withDeletedAt(page, null));
		return GbrainRestoreResult.RESTORED;
	}

	@Override
	public GbrainSearchResult search(GbrainSearchRequest request) {
		return new GbrainSearchResult(List.of(), new GbrainRetrieval(false, false, List.of()));
	}

	@Override
	public GbrainSynthesisResult synthesize(GbrainSynthesisRequest request) {
		throw new GbrainException(GbrainErrorCode.UNAVAILABLE, "gbrain synthesis is unavailable in in-memory mode");
	}

	private static GbrainStoredDocument withDeletedAt(GbrainStoredDocument page, Instant deletedAt) {
		return new GbrainStoredDocument(page.externalId(), page.title(), page.documentType(), page.revision(),
				page.contentDigest(), deletedAt);
	}

}

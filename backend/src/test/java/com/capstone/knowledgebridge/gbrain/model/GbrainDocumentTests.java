package com.capstone.knowledgebridge.gbrain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;

class GbrainDocumentTests {

	static final Instant CREATED = Instant.parse("2026-09-01T12:00:00Z");

	static final Instant UPDATED = Instant.parse("2026-09-02T08:30:00Z");

	@Test
	void normalizesLineEndingsByteOrderMarkAndTrailingWhitespace() {
		GbrainDocument document = document("item-1", "\uFEFF# Title\r\nLine two\rLine three  \n\n\n");

		assertThat(document.body()).isEqualTo("# Title\nLine two\nLine three\n");
		assertThat(document.contentDigest())
				.isEqualTo(document("item-1", "# Title\nLine two\nLine three").contentDigest());
	}

	@Test
	void lowerCasesTheDocumentType() {
		GbrainDocument document = new GbrainDocument("item-1", "Title", "Policy_Doc", "7", 1, CREATED, UPDATED,
				"Body");

		assertThat(document.documentType()).isEqualTo("policy_doc");
	}

	@Test
	void digestIsDeterministicAndCoversEveryIndexedField() {
		GbrainDocument base = document("item-1", "Body");

		assertThat(base.contentDigest()).hasSize(64).isEqualTo(document("item-1", "Body").contentDigest());
		assertThat(base.contentDigest()).isNotEqualTo(document("item-2", "Body").contentDigest())
				.isNotEqualTo(document("item-1", "Body changed").contentDigest())
				.isNotEqualTo(new GbrainDocument("item-1", "Title", "note", "7", 2, CREATED, UPDATED, "Body")
						.contentDigest())
				.isNotEqualTo(new GbrainDocument("item-1", "Other", "note", "7", 1, CREATED, UPDATED, "Body")
						.contentDigest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "Upper", "-leading", "has space", "has/slash",
			"a12345678901234567890123456789012345678901234567890123456789012345"})
	void rejectsInvalidItemKeys(String itemKey) {
		assertValidation(() -> document(itemKey, "Body"));
	}

	@Test
	void acceptsUuidAndNumericItemKeys() {
		assertThat(GbrainDocument.requireItemKey("3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
				.isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
		assertThat(GbrainDocument.requireItemKey("42")).isEqualTo("42");
		assertValidation(() -> GbrainDocument.requireItemKey(null));
	}

	@ParameterizedTest
	@ValueSource(strings = {" ", "line\nbreak", "tab\there", "sep\u2028arator", "para\u2029graph", "bom\uFEFF"})
	void rejectsUnsafeTitlesAndOwners(String value) {
		assertValidation(() -> new GbrainDocument("item-1", value, "note", "7", 1, CREATED, UPDATED, "Body"));
		assertValidation(() -> new GbrainDocument("item-1", "Title", "note", value, 1, CREATED, UPDATED, "Body"));
	}

	@Test
	void rejectsMissingOrOversizedFields() {
		assertValidation(() -> new GbrainDocument("item-1", null, "note", "7", 1, CREATED, UPDATED, "Body"));
		assertValidation(() -> new GbrainDocument("item-1", "x".repeat(GbrainDocument.MAX_TITLE_LENGTH + 1), "note",
				"7", 1, CREATED, UPDATED, "Body"));
		assertValidation(() -> new GbrainDocument("item-1", "Title", null, "7", 1, CREATED, UPDATED, "Body"));
		assertValidation(() -> new GbrainDocument("item-1", "Title", "has space", "7", 1, CREATED, UPDATED, "Body"));
		assertValidation(() -> new GbrainDocument("item-1", "Title", "note", null, 1, CREATED, UPDATED, "Body"));
		assertValidation(() -> new GbrainDocument("item-1", "Title", "note", "7", -1, CREATED, UPDATED, "Body"));
		assertValidation(() -> new GbrainDocument("item-1", "Title", "note", "7", 1, null, UPDATED, "Body"));
		assertValidation(() -> new GbrainDocument("item-1", "Title", "note", "7", 1, CREATED, null, "Body"));
	}

	@Test
	void rejectsEmptyBinaryOrOversizedBodies() {
		assertValidation(() -> document("item-1", null));
		assertValidation(() -> document("item-1", " \r\n\t"));
		assertValidation(() -> document("item-1", "a\u0000b"));
		assertValidation(() -> document("item-1", "x".repeat(GbrainDocument.MAX_BODY_BYTES)));
		assertThat(document("item-1", "x".repeat(GbrainDocument.MAX_BODY_BYTES - 1)).body())
				.hasSize(GbrainDocument.MAX_BODY_BYTES);
	}

	@Test
	void reportsAnUnavailableDigestAlgorithm() {
		assertThatThrownBy(() -> GbrainDocument.hexDigest("NOT-AN-ALGORITHM", "x"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void storedDocumentReportsReconciliationState() {
		GbrainDocument document = document("item-1", "Body");

		assertThat(stored(document.contentDigest(), null).stateFor(document)).isEqualTo(GbrainDocumentState.CURRENT);
		assertThat(stored("other", null).stateFor(document)).isEqualTo(GbrainDocumentState.STALE);
		assertThat(stored(null, null).stateFor(document)).isEqualTo(GbrainDocumentState.STALE);
		assertThat(stored(document.contentDigest(), UPDATED).stateFor(document))
				.isEqualTo(GbrainDocumentState.DELETED);
		assertThat(stored(null, UPDATED).deleted()).isTrue();
	}

	static GbrainDocument document(String itemKey, String body) {
		return new GbrainDocument(itemKey, "Title", "note", "7", 1, CREATED, UPDATED, body);
	}

	private static GbrainStoredDocument stored(String digest, Instant deletedAt) {
		return new GbrainStoredDocument("knowledgebridge/item-1", "Title", "note", 1L, digest, deletedAt);
	}

	private static void assertValidation(ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(GbrainException.class,
				exception -> assertThat(exception.code()).isEqualTo(GbrainErrorCode.VALIDATION));
	}

}

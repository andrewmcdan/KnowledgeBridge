package com.capstone.knowledgebridge.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;

class GbrainDocumentMapperTests {

	private static KnowledgeItem itemWithType(String type) {
		KnowledgeItem item = new KnowledgeItem("Title", type, 7L, "body text");
		// id is normally DB-generated; set it directly since this is a pure unit test with no database.
		ReflectionTestUtils.setField(item, "id", 42L);
		return item;
	}

	@Test
	void mapsFieldsAndUsesCreatedAtForBothTimestamps() {
		KnowledgeItem item = itemWithType("Policy");

		GbrainDocument document = GbrainDocumentMapper.toDocument(item);

		assertThat(document.itemKey()).isEqualTo("42");
		assertThat(document.title()).isEqualTo("Title");
		assertThat(document.documentType()).isEqualTo("policy");
		assertThat(document.ownerId()).isEqualTo("7");
		assertThat(document.revision()).isEqualTo(1L);
		// GbrainDocument's own compact constructor normalizes the body and always appends exactly one
		// trailing newline - not something the mapper does itself, just worth asserting we know about it.
		assertThat(document.body()).isEqualTo("body text\n");
		// The digest-stability fix: both timestamp slots use createdAt, never updatedAt.
		assertThat(document.createdAt()).isEqualTo(item.getCreatedAt());
		assertThat(document.updatedAt()).isEqualTo(item.getCreatedAt());
	}

	@Test
	void slugifyCollapsesSpacesToHyphens() {
		GbrainDocument document = GbrainDocumentMapper.toDocument(itemWithType("Finance Policy"));

		assertThat(document.documentType()).isEqualTo("finance-policy");
	}

	@Test
	void slugifyLeavesAlreadyValidTypeAlone() {
		GbrainDocument document = GbrainDocumentMapper.toDocument(itemWithType("policy_doc"));

		assertThat(document.documentType()).isEqualTo("policy_doc");
	}

	@Test
	void slugifyFallsBackToDocumentWhenNothingSurvives() {
		GbrainDocument document = GbrainDocumentMapper.toDocument(itemWithType("!!!"));

		assertThat(document.documentType()).isEqualTo("document");
	}

	@Test
	void slugifyTruncatesOverlyLongTypes() {
		String longType = "a".repeat(100);

		GbrainDocument document = GbrainDocumentMapper.toDocument(itemWithType(longType));

		assertThat(document.documentType()).hasSize(64);
	}

}

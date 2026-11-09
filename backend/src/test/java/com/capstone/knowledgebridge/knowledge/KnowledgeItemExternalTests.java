package com.capstone.knowledgebridge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class KnowledgeItemExternalTests {

	@Test
	void recordsWhereASyncedItemCameFrom() {
		Instant updated = Instant.parse("2026-11-05T22:37:59Z");

		KnowledgeItem item = KnowledgeItem.external("ACME-5: Rollback drill", "task", 3L, "# Body",
				KnowledgeSourceKind.JIRA, "ACME-5", "https://example.atlassian.net/browse/ACME-5", updated);

		assertThat(item.getSourceKind()).isEqualTo(KnowledgeSourceKind.JIRA);
		assertThat(item.getExternalSource()).isEqualTo("JIRA");
		assertThat(item.getExternalKey()).isEqualTo("ACME-5");
		assertThat(item.getExternalUrl()).isEqualTo("https://example.atlassian.net/browse/ACME-5");
		assertThat(item.getExternalUpdatedAt()).isEqualTo(updated);
		assertThat(item.getStatus()).isEqualTo(KnowledgeItemStatus.PENDING);
	}

	@Test
	void uploadsHaveNoExternalSource() {
		KnowledgeItem item = new KnowledgeItem("Title", "note", 3L, "Body");

		assertThat(item.getExternalSource()).isNull();
		assertThat(item.getExternalKey()).isNull();
		assertThat(item.getExternalUrl()).isNull();
		assertThat(item.getExternalUpdatedAt()).isNull();
	}

}

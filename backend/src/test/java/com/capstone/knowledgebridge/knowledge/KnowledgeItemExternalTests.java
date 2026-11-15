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
	void anExternalUpdateReplacesContentAndAdvancesTheRevision() {
		Instant first = Instant.parse("2026-11-05T22:37:59Z");
		Instant second = first.plusSeconds(60);
		KnowledgeItem item = KnowledgeItem.external("Old", "task", 3L, "old", KnowledgeSourceKind.JIRA, "ACME-5",
				"https://old.example/ACME-5", first);
		Instant contentUpdatedAt = item.getContentUpdatedAt();

		item.applyExternalUpdate("New", "bug", "new body", "https://new.example/ACME-5", second);

		assertThat(item.getTitle()).isEqualTo("New");
		assertThat(item.getType()).isEqualTo("bug");
		assertThat(item.getBodyText()).isEqualTo("new body");
		assertThat(item.getCharacterCount()).isEqualTo(8);
		assertThat(item.getExternalUrl()).isEqualTo("https://new.example/ACME-5");
		assertThat(item.getExternalUpdatedAt()).isEqualTo(second);
		assertThat(item.getRevision()).isEqualTo(2);
		assertThat(item.getContentUpdatedAt()).isAfterOrEqualTo(contentUpdatedAt);
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

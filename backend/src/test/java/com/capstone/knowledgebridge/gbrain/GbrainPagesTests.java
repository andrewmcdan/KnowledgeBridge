package com.capstone.knowledgebridge.gbrain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;

class GbrainPagesTests {

	@Test
	void mapsItemKeysIntoTheBoundSlugPrefix() {
		assertThat(GbrainPages.slugFor("3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
				.isEqualTo("knowledgebridge/3f2504e0-4f89-11d3-9a0c-0305e82c3301");
		GbrainMcpClientTests.assertCode(() -> GbrainPages.slugFor("../escape"), GbrainErrorCode.VALIDATION);
	}

	@Test
	void rendersCompleteMarkdownWithControlledFrontmatter() {
		GbrainDocument document = new GbrainDocument("item-1", "Travel \"Expense\" C:\\Policy", "policy", "7", 3,
				Instant.parse("2026-09-01T12:00:00Z"), Instant.parse("2026-09-02T08:30:00Z"),
				"---\nid: someone-else\nvisibility: private\n---\n# Body\n");

		assertThat(GbrainPages.render(document)).isEqualTo("""
				---
				title: "Travel \\"Expense\\" C:\\\\Policy"
				type: "policy"
				knowledgebridge_id: "item-1"
				knowledgebridge_owner: "7"
				knowledgebridge_revision: 3
				knowledgebridge_created_at: "2026-09-01T12:00:00Z"
				knowledgebridge_updated_at: "2026-09-02T08:30:00Z"
				knowledgebridge_digest: "%s"
				---

				---
				id: someone-else
				visibility: private
				---
				# Body
				""".formatted(document.contentDigest()));
	}

}

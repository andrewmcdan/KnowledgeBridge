package com.capstone.knowledgebridge.jiraintegration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.ingestion.IngestionService;
import com.capstone.knowledgebridge.jira.model.JiraComment;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraIssueLink;

class JiraMarkdownConverterTests {

	private static final Instant TIME = Instant.parse("2026-11-04T12:00:00Z");

	@Test
	void rendersACompletePage() {
		String page = JiraMarkdownConverter.toMarkdown(
				issue("KB-42", "Migrate billing", "Jane Doe", "KB-10", "Billing epic", "Body text\n"),
				List.of(comment("1", "Sam Lee", TIME, "  Looks good.  ")));

		assertThat(page).isEqualTo("""
				# KB-42: Migrate billing

				Projects: Billing Platform\s\s
				Type: Story\s\s
				Status: In Progress\s\s
				Priority: High\s\s
				Assignee: Jane Doe\s\s
				Reporter: Sam Lee\s\s
				Labels: migration, q4\s\s
				Parent: KB-10 (Billing epic)\s\s
				Updated: 2026-11-04\s\s
				Link: https://example.atlassian.net/browse/KB-42\s\s

				## Description

				Body text

				## Links

				- blocks KB-43

				## Comments

				**Sam Lee, 2026-11-04:** Looks good.

				""");
	}

	@Test
	void leavesOutWhatTheWorkItemDoesNotHave() {
		JiraIssue issue = new JiraIssue("1", "KB-1", "Summary", null, null, null, null, null, List.of(), null, null,
				null,
				null, TIME, TIME, " ", List.of(), 0, List.of(), "https://example.atlassian.net/browse/KB-1");

		String page = JiraMarkdownConverter.toMarkdown(issue, List.of());

		assertThat(page).contains("Assignee: Unassigned")
				.doesNotContain("Projects:", "Type:", "Status:", "Priority:", "Reporter:", "Labels:", "Parent:",
						"## Description", "## Links", "## Comments");
	}

	@Test
	void hidesSeedBookkeepingLabels() {
		JiraIssue issue = new JiraIssue("1", "KB-1", "S", null, null, null, null, null,
				List.of("kb-seed", "kbseed-cedar-epic", "cedar"), null, null, null, null, TIME, TIME, "", List.of(), 0,
				List.of(), "https://example.atlassian.net/browse/KB-1");

		assertThat(JiraMarkdownConverter.toMarkdown(issue, List.of())).contains("Labels: cedar  \n").doesNotContain(
				"kb-seed",
				"kbseed-");
	}

	@Test
	void showsAParentWithoutASummaryByKey() {
		assertThat(JiraMarkdownConverter.toMarkdown(issue("KB-1", "S", null, "KB-2", null, ""), List.of()))
				.contains("Parent: KB-2  \n");
	}

	@Test
	void namesCommentsWithoutAnAuthor() {
		assertThat(JiraMarkdownConverter.toMarkdown(issue("KB-1", "S", null, null, null, ""),
				List.of(comment("1", null, TIME, "text")))).contains("**Unknown, 2026-11-04:** text");
	}

	@Test
	void ordersCommentsOldestFirstWhateverOrderTheyArrive() {
		String page = JiraMarkdownConverter.toMarkdown(issue("KB-1", "S", null, null, null, ""),
				List.of(comment("2", "B", TIME, "newer"), comment("1", "A", TIME.minusSeconds(60), "older")));

		assertThat(page.indexOf("older")).isLessThan(page.indexOf("newer"));
	}

	@Test
	void headerValuesCannotAddLinesOfTheirOwn() {
		JiraIssue issue = issue("KB-1", "Summary\nTeam: Injected", "Jane\nProjects: Fake", null, null, "");

		assertThat(JiraMarkdownConverter.toMarkdown(issue, List.of()))
				.doesNotContainPattern("(?m)^(Team|Projects): (Injected|Fake)")
				.contains("Assignee: Jane Projects: Fake");
	}

	@Test
	void dropsTheOldestCommentsToStayUnderTheBodyLimit() {
		List<JiraComment> comments = new ArrayList<>();
		String body = "x".repeat(300_000);
		for (int index = 0; index < 5; index++) {
			comments.add(comment(Integer.toString(index), "A", TIME.plusSeconds(index), "marker" + index + body));
		}

		String page = JiraMarkdownConverter.toMarkdown(issue("KB-1", "S", null, null, null, "Description"), comments);

		assertThat(IngestionService.exceedsBodyLimit(page)).isFalse();
		assertThat(page).contains("_2 older comments omitted to fit the size limit._", "marker2", "marker3", "marker4")
				.doesNotContain("marker0", "marker1");
	}

	@Test
	void keepsAPageThatExactlyFitsWithoutAnOmissionNote() {
		String page = JiraMarkdownConverter.toMarkdown(issue("KB-1", "S", null, null, null, ""),
				List.of(comment("1", "A", TIME, "x".repeat(GbrainDocument.MAX_BODY_BYTES / 2))));

		assertThat(page).doesNotContain("omitted");
	}

	@Test
	void countsMultiByteCharactersAgainstTheLimit() {
		String page = JiraMarkdownConverter.toMarkdown(issue("KB-1", "S", null, null, null, ""),
				List.of(comment("1", "A", TIME, "é".repeat(600_000))));

		assertThat(page).contains("_1 older comments omitted");
		assertThat(IngestionService.exceedsBodyLimit(page)).isFalse();
	}

	@Test
	void cutsLongTitlesWithoutSplittingAnEmoji() {
		String emoji = "😀";
		String filler = "a".repeat(GbrainDocument.MAX_TITLE_LENGTH - "KB-1: ".length() - 1);

		String title = JiraMarkdownConverter.title(issue("KB-1", filler + emoji, null, null, null, ""));

		assertThat(title).isEqualTo("KB-1: " + filler);
		assertThat(JiraMarkdownConverter.title(issue("KB-1", "Short", null, null, null, ""))).isEqualTo("KB-1: Short");
	}

	private static JiraIssue issue(String key, String summary, String assignee, String parentKey, String parentSummary,
			String description) {
		return new JiraIssue("1", key, summary, "KB", "Billing Platform", "Story", "In Progress", "High",
				List.of("migration", "q4"), assignee, "Sam Lee", parentKey, parentSummary, TIME, TIME, description,
				List.of(),
				0, "KB-42".equals(key) ? List.of(new JiraIssueLink("blocks", "KB-43")) : List.of(),
				"https://example.atlassian.net/browse/" + key);
	}

	private static JiraComment comment(String id, String author, Instant created, String body) {
		return new JiraComment(id, author, created, body);
	}

}

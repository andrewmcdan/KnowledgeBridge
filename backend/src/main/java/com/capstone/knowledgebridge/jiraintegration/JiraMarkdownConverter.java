package com.capstone.knowledgebridge.jiraintegration;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.jira.model.JiraComment;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraIssueLink;

/**
 * Builds the Markdown page gbrain stores for one work item. The header's {@code Projects:} line feeds the project
 * entity hints, so it must stay at the start of its own line.
 */
public final class JiraMarkdownConverter {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

	/** Leaves room for the comments heading and the omission note. */
	private static final int RESERVED_BYTES = 128;

	/** Two trailing spaces make a Markdown hard break, so the header renders as separate lines. */
	private static final String HEADER_LINE_END = "  \n";

	private JiraMarkdownConverter() {
	}

	/** The knowledge item title, cut to the length gbrain accepts without splitting a surrogate pair. */
	public static String title(JiraIssue issue) {
		String title = clean(issue.key() + ": " + issue.summary());
		return title.length() <= GbrainDocument.MAX_TITLE_LENGTH
				? title
				: title.substring(0, GbrainDocument.MAX_TITLE_LENGTH).replaceAll("\\p{Cs}$", "");
	}

	/**
	 * Renders the page. {@code comments} should be the work item's complete list; when the page would exceed gbrain's
	 * body limit, the oldest comments are dropped and the page says how many.
	 */
	public static String toMarkdown(JiraIssue issue, List<JiraComment> comments) {
		StringBuilder page = new StringBuilder("# ").append(title(issue)).append("\n\n");
		appendHeader(page, issue);
		if (!issue.description().isBlank()) {
			page.append("\n## Description\n\n").append(issue.description().strip()).append('\n');
		}
		if (!issue.links().isEmpty()) {
			page.append("\n## Links\n\n");
			for (JiraIssueLink link : issue.links()) {
				page.append("- ").append(link.relation()).append(' ').append(link.otherKey()).append('\n');
			}
		}
		appendComments(page, comments);
		return page.toString();
	}

	private static void appendHeader(StringBuilder page, JiraIssue issue) {
		field(page, "Projects", issue.projectName());
		field(page, "Type", issue.type());
		field(page, "Status", issue.status());
		field(page, "Priority", issue.priority());
		field(page, "Assignee", Objects.requireNonNullElse(issue.assignee(), "Unassigned"));
		field(page, "Reporter", issue.reporter());
		field(page, "Labels", String.join(", ", visibleLabels(issue)));
		field(page, "Parent", parent(issue));
		field(page, "Updated", date(issue.updated()));
		field(page, "Link", issue.browseUrl());
	}

	/** The seed script's bookkeeping labels mean nothing to a reader or to the AI. */
	private static List<String> visibleLabels(JiraIssue issue) {
		return issue.labels().stream().filter(label -> !label.equals("kb-seed") && !label.startsWith("kbseed-"))
				.toList();
	}

	private static String parent(JiraIssue issue) {
		if (issue.parentKey() == null || issue.parentSummary() == null) {
			return issue.parentKey();
		}
		return issue.parentKey() + " (" + issue.parentSummary() + ")";
	}

	private static void field(StringBuilder page, String label, String value) {
		if (value != null && !value.isBlank()) {
			page.append(label).append(": ").append(clean(value)).append(HEADER_LINE_END);
		}
	}

	private static void appendComments(StringBuilder page, List<JiraComment> comments) {
		if (comments.isEmpty()) {
			return;
		}
		long budget = GbrainDocument.MAX_BODY_BYTES - bytes(page.toString()) - RESERVED_BYTES;
		Deque<String> kept = new ArrayDeque<>();
		List<JiraComment> newestFirst = comments.stream()
				.sorted(Comparator.comparing(JiraComment::created).reversed())
				.toList();
		for (JiraComment comment : newestFirst) {
			String block = block(comment);
			budget -= bytes(block);
			if (budget < 0) {
				break;
			}
			kept.addFirst(block);
		}
		page.append("\n## Comments\n\n");
		int omitted = comments.size() - kept.size();
		if (omitted > 0) {
			page.append('_').append(omitted).append(" older comments omitted to fit the size limit._\n\n");
		}
		kept.forEach(page::append);
	}

	private static String block(JiraComment comment) {
		return "**" + clean(Objects.requireNonNullElse(comment.author(), "Unknown")) + ", " + date(comment.created())
				+ ":** " + comment.body().strip() + "\n\n";
	}

	private static String date(Instant instant) {
		return DATE.format(instant);
	}

	/** Keeps single-line values on one line, so Jira text can't add header fields of its own. */
	private static String clean(String value) {
		return value.replaceAll("\\s+", " ").strip();
	}

	private static int bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8).length;
	}

}

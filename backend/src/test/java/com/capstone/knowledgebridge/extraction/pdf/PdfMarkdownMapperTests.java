package com.capstone.knowledgebridge.extraction.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class PdfMarkdownMapperTests {

	private static final float HEIGHT = 800;

	/** Enough 10pt text that 10pt is the body size in every test. */
	private static final String BODY = "Body text that sets the document's body size to ten points for every test here";

	private static PdfLine line(String text, float y) {
		return new PdfLine(text, 10, false, 72, y);
	}

	private static PdfLine line(String text, float fontSize, boolean bold, float y) {
		return new PdfLine(text, fontSize, bold, 72, y);
	}

	private static PdfLine indented(String text, float x, float y) {
		return new PdfLine(text, 10, false, x, y);
	}

	private static PdfPage page(PdfLine... lines) {
		return new PdfPage(1, List.of(lines), HEIGHT, 0);
	}

	private static String markdown(PdfLine... lines) {
		return PdfMarkdownMapper.toMarkdown(List.of(page(lines)));
	}

	@Test
	void documentWithoutTextIsEmpty() {
		assertThat(PdfMarkdownMapper.toMarkdown(List.of(page(), page()))).isEmpty();
		assertThat(PdfMarkdownMapper.toMarkdown(List.of())).isEmpty();
	}

	@Test
	void largerSizesBecomeHeadingsRankedLargestFirstAndCappedAtThreeLevels() {
		assertThat(
				markdown(line("Handbook", 24, false, 200), line("Scope", 18, true, 240), line("Detail", 14, true, 280),
						line("Deeper", 13, false, 320), line(BODY, 360)))
				.isEqualTo("""
						# Handbook

						## Scope

						### Detail

						### Deeper

						%s
						""".formatted(BODY));
	}

	@Test
	void slightlyLargerTextIsAHeadingOnlyWhenBold() {
		assertThat(markdown(line("Morgan Reed", 11, false, 200), line("Escalation", 11, true, 240), line(BODY, 280)))
				.isEqualTo("""
						Morgan Reed

						# Escalation

						%s
						""".formatted(BODY));
	}

	@Test
	void boldBodySizeSentencesLongLinesAndListItemsAreNotHeadings() {
		String longLine = "L".repeat(121);
		assertThat(markdown(line("Item Allowance Cycle", 10, true, 200), line("Ends in a period.", 18, true, 240),
				line(longLine, 18, true, 280), line("1. Numbered", 18, true, 320), line(BODY, 360), line(BODY, 374)))
				.isEqualTo("""
						Item Allowance Cycle

						Ends in a period.

						%s

						1. Numbered

						%s
						%2$s
						""".formatted(longLine, BODY));
	}

	@Test
	void headingWrappedOverLinesBecomesOneHeadingButSeparatedHeadingsStaySeparate() {
		assertThat(markdown(line("Harbor Partner", 24, true, 100), line("Integration Runbook", 24, true, 128),
				line("Second Title", 24, true, 300), line("Section", 18, true, 324), line(BODY, 400)))
				.isEqualTo("""
						# Harbor Partner Integration Runbook

						# Second Title

						## Section

						%s
						""".formatted(BODY));
	}

	@Test
	void linesCloseTogetherFormAParagraphAndALargeGapStartsAnother() {
		assertThat(markdown(line("First line of the", 100), line("first paragraph.", 114), line(BODY, 160)))
				.isEqualTo("""
						First line of the
						first paragraph.

						%s
						""".formatted(BODY));
	}

	@Test
	void wordHyphenatedAcrossLinesIsRejoined() {
		assertThat(markdown(line("reconcili-", 100), line("ation window and an inter‐", 114),
				line("national partner", 128), line(BODY, 300)))
				.startsWith("reconciliation window and an international partner\n\n");
	}

	@Test
	void hyphenBeforeAnUppercaseWordOrAfterANonLetterIsKept() {
		assertThat(markdown(line("Acme-", 100), line("Bridge sync", 114), line("pages 10-", 128), line("12 apply", 142),
				line(BODY, 300))).startsWith("Acme-\nBridge sync\npages 10-\n12 apply\n\n");
	}

	@Test
	void paragraphContinuesAfterAPageOrColumnBreakWhenTheNextLineStartsInLowercase() {
		PdfPage first = new PdfPage(1, List.of(line(BODY, 100), line("Samira", 700)), HEIGHT, 0);
		PdfPage second = new PdfPage(2, List.of(line("will update the message.", 300), line("Next paragraph.", 314)),
				HEIGHT, 0);

		assertThat(PdfMarkdownMapper.toMarkdown(List.of(first, second))).isEqualTo("""
				%s

				Samira
				will update the message.
				Next paragraph.
				""".formatted(BODY));
	}

	@Test
	void uppercaseLineAfterABreakStartsANewParagraph() {
		assertThat(markdown(line(BODY, 500), line("Column two starts here", 100))).isEqualTo("""
				%s

				Column two starts here
				""".formatted(BODY));
	}

	@Test
	void bulletGlyphsBecomeDashesAndNumberedItemsAreKept() {
		assertThat(markdown(line(BODY, 100), line(" Symbol bullet", 140), line("•Round bullet", 154),
				line("- Already Markdown", 168), line("2) Second step", 182))).isEqualTo("""
						%s

						- Symbol bullet
						- Round bullet
						- Already Markdown
						2) Second step
						""".formatted(BODY));
	}

	@Test
	void listItemsNestByIndentationAndReturnToOuterLevels() {
		assertThat(markdown(line(BODY, 60), indented("1. Check the feed:", 72, 100),
				indented("• Webhook feeds", 90, 114), indented("◦ Replay delivery", 108, 128),
				indented("• Batch feeds", 90.5f, 142), indented("2. Record the outcome", 72, 156)))
				.isEqualTo("""
						%s

						1. Check the feed:
						    - Webhook feeds
						        - Replay delivery
						    - Batch feeds
						2. Record the outcome
						""".formatted(BODY));
	}

	@Test
	void wrappedItemTextIsJoinedToItsItemIncludingHyphenatedWords() {
		assertThat(markdown(line(BODY, 60), indented("3. Rotate the credential in the dash-", 72, 100),
				indented("board. Never paste", 86, 114), indented("credentials into a ticket.", 86, 128)))
				.isEqualTo("""
						%s

						3. Rotate the credential in the dashboard. Never paste credentials into a ticket.
						""".formatted(BODY));
	}

	@Test
	void unindentedLineAfterAnItemStartsAParagraphAndClosesTheList() {
		assertThat(markdown(line(BODY, 60), indented("- Outer", 72, 100), indented("- Inner", 90, 114),
				indented("Closing remark", 72, 128), indented("- New list", 90, 170))).isEqualTo("""
						%s

						- Outer
						    - Inner

						Closing remark

						- New list
						""".formatted(BODY));
	}

	@Test
	void itemsSeparatedByLargeGapsKeepTheirNestingInSeparateBlocks() {
		assertThat(markdown(line(BODY, 60), indented("1. First", 72, 100), indented("- Nested", 90, 130),
				indented("indented text after a gap", 104, 160))).isEqualTo("""
						%s

						1. First

						    - Nested

						indented text after a gap
						""".formatted(BODY));
	}

	private static PdfLine cell(String text, float x, float y) {
		return new PdfLine(text, 10, false, x, y);
	}

	@Test
	void rowsOfAlignedCellsBecomeAMarkdownTable() {
		assertThat(markdown(line(BODY, 60), cell("Situation", 72, 100), cell("Owner", 200, 100),
				cell("Response target", 320, 100), cell("Feed down", 72, 130), cell("Webhook | Batch", 200, 130),
				cell("15 minutes", 320, 130), cell("Duplicates", 72, 160), cell("Morgan Reed", 200, 160),
				cell("4 hours", 320, 160), line("Closing remark", 200))).isEqualTo("""
						%s

						| Situation | Owner | Response target |
						| --- | --- | --- |
						| Feed down | Webhook \\| Batch | 15 minutes |
						| Duplicates | Morgan Reed | 4 hours |

						Closing remark
						""".formatted(BODY));
	}

	@Test
	void rowsWithFewerCellsFillTheNearestColumnsOfTheWidestDataRow() {
		// A centered header row, a full data row, and shorter rows that line up with it.
		assertThat(markdown(line(BODY, 60), cell("Situation", 100, 100), cell("Owner", 230, 100),
				cell("Target", 350, 100), cell("Feed down", 72, 130), cell("On-call", 200, 130),
				cell("15 minutes", 320, 130), cell("Partner exposure", 72, 160), cell("Immediately", 322, 160),
				cell("Two", 72, 190), cell("parts", 75, 190))).isEqualTo("""
						%s

						| Situation | Owner | Target |
						| --- | --- | --- |
						| Feed down | On-call | 15 minutes |
						| Partner exposure |  | Immediately |
						| Two parts |  |  |
						""".formatted(BODY));
	}

	@Test
	void cellsOutsideATableStaySeparateLinesSoNamesDoNotRunTogether() {
		assertThat(markdown(line(BODY, 60), cell("Samira Cole", 72, 100), cell("Customer Success", 200, 100),
				cell("Avery", 72, 114), cell("Stone", 150, 114), cell("Sales", 260, 114), cell("Later", 200, 128),
				cell("Earlier", 72, 128))).isEqualTo("""
						%s

						Samira Cole
						Customer Success
						Avery
						Stone
						Sales
						Later
						Earlier
						""".formatted(BODY));
	}

	@Test
	void listMarkersAndHeadingsSplitByAGapBecomeOneLineAgain() {
		assertThat(markdown(line(BODY, 60), line("1.1", 14, true, 100), new PdfLine("Escalation", 14, true, 100, 100),
				cell("\uF0B7", 72, 140), cell("Managers approve", 90, 140), cell("2.", 72, 154),
				cell("Attach approval", 90, 154))).isEqualTo("""
						%s

						# 1.1 Escalation

						- Managers approve
						2. Attach approval
						""".formatted(BODY));
	}

	@Test
	void repeatedMarginLinesAreDroppedAfterTheirFirstOccurrence() {
		List<PdfPage> pages = new ArrayList<>();
		for (int number = 1; number <= 3; number++) {
			pages.add(new PdfPage(number, List.of(line("Acme Confidential", 40), line(BODY + " " + number, 300),
					line("Page " + number + " of 3", 780)), HEIGHT, 0));
		}

		assertThat(PdfMarkdownMapper.toMarkdown(pages)).isEqualTo("""
				Acme Confidential

				%1$s 1

				Page 1 of 3

				%1$s 2

				%1$s 3
				""".formatted(BODY));
	}

	@Test
	void marginLinesOnFewerThanHalfThePagesOrOutsideTheMarginAreKept() {
		List<PdfPage> pages = new ArrayList<>();
		for (int number = 1; number <= 5; number++) {
			List<PdfLine> lines = new ArrayList<>(List.of(line("Repeated body line", 400)));
			if (number <= 2) {
				lines.add(line("Draft", 780));
			}
			pages.add(new PdfPage(number, lines, HEIGHT, 0));
		}

		String markdown = PdfMarkdownMapper.toMarkdown(pages);

		assertThat(Arrays.stream(markdown.split("\n")).filter("Repeated body line"::equals)).hasSize(5);
		assertThat(Arrays.stream(markdown.split("\n")).filter("Draft"::equals)).hasSize(2);
	}

}

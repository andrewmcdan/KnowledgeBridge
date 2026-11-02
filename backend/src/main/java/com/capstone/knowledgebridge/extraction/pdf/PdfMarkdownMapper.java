package com.capstone.knowledgebridge.extraction.pdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns extracted PDF lines into Markdown with deliberately simple layout rules: larger text becomes a heading, a large
 * vertical gap starts a new block, bullet glyphs and numbers become list items nested by indentation, words hyphenated
 * across lines are rejoined, and text repeated in the page margins is dropped as a running header or footer. No PDFBox
 * here, so every rule can be tested with plain records. See the package README for the rules and their known
 * limitations.
 */
final class PdfMarkdownMapper {

	/** Text at least this much larger than body text can be a heading, bold or not. */
	private static final float HEADING_SIZE_RATIO = 1.2f;

	/** Bold text at least this many points larger than body text can also be a heading. */
	private static final float BOLD_HEADING_SIZE_MARGIN = 1f;

	/** Longer lines are large-print paragraphs, not headings. */
	private static final int MAX_HEADING_LENGTH = 120;

	private static final int MAX_HEADING_LEVEL = 3;

	/** A vertical gap larger than this multiple of the font size starts a new block. */
	private static final float BLOCK_GAP = 1.5f;

	/**
	 * Running headers and footers are looked for in this fraction of the page at the top and at the bottom; LaTeX's
	 * default layout puts the page number about 17% from the bottom of an A4 page.
	 */
	private static final float MARGIN_BAND = 0.2f;

	/** Left edges this close together, in points, count as the same indentation. */
	private static final float INDENT_TOLERANCE = 2f;

	private static final String LIST_INDENT = "    ";

	/**
	 * Bullet glyphs PDFs commonly use, including the private-use code points of the Symbol (U+F0B7) and Wingdings
	 * (U+F0A7) bullets that Word and LibreOffice emit, plus Markdown-style "-" and "*" followed by a space.
	 */
	private static final Pattern BULLET = Pattern
			.compile("^(?:[\\u2022\\u2023\\u2043\\u25AA\\u25CF\\u25E6\\uF0A7\\uF0B7]\\s*|[-*]\\s+)");

	private static final Pattern NUMBERED = Pattern.compile("^\\d{1,9}[.)]\\s");

	/** A letter followed by a hyphen, a Unicode hyphen (U+2010), or a soft hyphen at the end of a line. */
	private static final Pattern LINE_END_HYPHEN = Pattern.compile("\\p{L}[-\\u2010\\u00AD]$");

	private static final Pattern DIGITS = Pattern.compile("\\d+");

	/** A cell holding only a list marker: a bullet glyph, "-" or "*", "1." or "1)", or "a." or "a)". */
	private static final Pattern LIST_MARKER = Pattern
			.compile("[\\u2022\\u2023\\u2043\\u25AA\\u25CF\\u25E6\\uF0A7\\uF0B7*-]|\\d{1,9}[.)]|[a-z][.)]");

	/** Cells whose baselines are this close, in points, are on the same row. */
	private static final float ROW_TOLERANCE = 1f;

	/** Cells of consecutive table rows starting this close, in points, are in the same column. */
	private static final float COLUMN_TOLERANCE = 3f;

	/** Fewer consecutive rows of cells are aligned text, such as a label and value, rather than a table. */
	private static final int MIN_TABLE_ROWS = 2;

	private PdfMarkdownMapper() {
	}

	/** Returns the pages as Markdown, or an empty string when they have no text. */
	static String toMarkdown(List<PdfPage> pages) {
		List<List<PdfLine>> content = withoutRunningHeadersAndFooters(pages);
		Layout layout = Layout.of(content.stream().flatMap(List::stream).toList());
		MarkdownWriter writer = new MarkdownWriter(layout);
		for (List<PdfLine> page : content) {
			writer.newPage();
			List<List<PdfLine>> rows = rows(page);
			int start = 0;
			while (start < rows.size()) {
				int end = start;
				while (end < rows.size() && isTableRow(rows.get(end), layout)
						&& (end == start || sharesColumn(rows.get(end - 1), rows.get(end)))) {
					end++;
				}
				if (end - start >= MIN_TABLE_ROWS) {
					writer.addTable(rows.subList(start, end));
					start = end;
				} else {
					addRow(rows.get(start), layout, writer);
					start++;
				}
			}
		}
		return writer.finish();
	}

	/**
	 * Groups a page's lines into rows: the extractor splits a line into cells at wide gaps, and consecutive cells at
	 * the same height, left to right, are one row.
	 */
	private static List<List<PdfLine>> rows(List<PdfLine> page) {
		List<List<PdfLine>> rows = new ArrayList<>();
		for (PdfLine line : page) {
			List<PdfLine> row = rows.isEmpty() ? null : rows.getLast();
			if (row != null && Math.abs(line.y() - row.getLast().y()) <= ROW_TOLERANCE
					&& line.x() > row.getLast().x()) {
				row.add(line);
			} else {
				rows.add(new ArrayList<>(List.of(line)));
			}
		}
		return rows;
	}

	/**
	 * A table row has two or more cells, none of them heading text. A row whose first cell is only a list marker is a
	 * list item with a tab after its marker, not a table row.
	 */
	private static boolean isTableRow(List<PdfLine> row, Layout layout) {
		return row.size() >= 2 && !LIST_MARKER.matcher(row.getFirst().text()).matches()
				&& row.stream().allMatch(cell -> layout.headingLevel(cell) == 0);
	}

	/**
	 * Consecutive table rows line up: a cell after the first starts within COLUMN_TOLERANCE of a cell after the first
	 * in the row above, or the rows have the same number of cells. Lines of a justified paragraph that happen to split
	 * into cells do neither.
	 */
	private static boolean sharesColumn(List<PdfLine> above, List<PdfLine> row) {
		return row.size() == above.size() || row.stream()
				.skip(1)
				.anyMatch(cell -> above.stream().skip(1)
						.anyMatch(other -> Math.abs(other.x() - cell.x()) <= COLUMN_TOLERANCE));
	}

	/**
	 * Writes a row that isn't part of a table. A list item with a tab after its marker, or a heading with a gap in it,
	 * becomes one line again. Other cells stay separate lines of the same paragraph, so text from neighbouring cells,
	 * such as "Batch" and "Morgan Reed", isn't read as one name.
	 */
	private static void addRow(List<PdfLine> row, Layout layout, MarkdownWriter writer) {
		if (row.size() == 1) {
			writer.add(row.getFirst());
		} else if (LIST_MARKER.matcher(row.getFirst().text()).matches()
				|| row.stream().anyMatch(cell -> layout.headingLevel(cell) > 0)) {
			writer.add(merged(row));
		} else {
			row.forEach(writer::add);
		}
	}

	/**
	 * Joins a row's cells into one line that starts where the row does and takes its size and boldness from the longest
	 * cell, so a bullet glyph doesn't decide them.
	 */
	private static PdfLine merged(List<PdfLine> row) {
		PdfLine first = row.getFirst();
		PdfLine longest = row.stream().max(Comparator.comparingInt(cell -> cell.text().length())).orElseThrow();
		String text = String.join(" ", row.stream().map(PdfLine::text).toList());
		return new PdfLine(text, longest.fontSize(), longest.bold(), first.x(), first.y());
	}

	/**
	 * Drops repeats of lines in the top or bottom margin band whose text, with digits ignored so page numbers match,
	 * appears at the same height on at least two pages and on at least half of them. The first occurrence is kept, so a
	 * table header row repeated on every page still appears once. A one-page document keeps everything. Returns the
	 * remaining lines of each page.
	 */
	private static List<List<PdfLine>> withoutRunningHeadersAndFooters(List<PdfPage> pages) {
		Map<String, Integer> pagesPerKey = new HashMap<>();
		for (PdfPage page : pages) {
			page.lines()
					.stream()
					.filter(line -> inMarginBand(line, page))
					.map(PdfMarkdownMapper::runningKey)
					.distinct()
					.forEach(key -> pagesPerKey.merge(key, 1, Integer::sum));
		}
		List<List<PdfLine>> content = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (PdfPage page : pages) {
			List<PdfLine> lines = new ArrayList<>();
			content.add(lines);
			for (PdfLine line : page.lines()) {
				if (!inMarginBand(line, page) || !isRunning(pagesPerKey.get(runningKey(line)), pages.size())
						|| seen.add(runningKey(line))) {
					lines.add(line);
				}
			}
		}
		return content;
	}

	private static boolean inMarginBand(PdfLine line, PdfPage page) {
		return line.y() < page.height() * MARGIN_BAND || line.y() > page.height() * (1 - MARGIN_BAND);
	}

	private static String runningKey(PdfLine line) {
		return DIGITS.matcher(line.text()).replaceAll("#") + "@" + Math.round(line.y());
	}

	private static boolean isRunning(int pageCount, int totalPages) {
		return pageCount >= 2 && pageCount * 2 >= totalPages;
	}

	/** Returns the line as a Markdown list item, or null when it isn't one. */
	private static String listItem(PdfLine line) {
		Matcher bullet = BULLET.matcher(line.text());
		if (bullet.find()) {
			return "- " + line.text().substring(bullet.end());
		}
		return NUMBERED.matcher(line.text()).find() ? line.text() : null;
	}

	private enum Block {
		NONE, HEADING, PARAGRAPH, LIST, TABLE
	}

	/**
	 * Builds Markdown one line at a time across all pages, so a paragraph or list can continue over a page or column
	 * break. Each block is a heading, a paragraph, or a run of list items; blocks are separated by a blank line.
	 */
	private static final class MarkdownWriter {

		private final Layout layout;

		private final List<String> blocks = new ArrayList<>();

		private final List<String> current = new ArrayList<>();

		private Block block = Block.NONE;

		private PdfLine previous;

		/** Left edges of the open list's nesting levels, outermost first. A heading or paragraph closes the list. */
		private final List<Float> listIndents = new ArrayList<>();

		private float lastItemX;

		/** The next line is the first on a new page. */
		private boolean pageBreak;

		MarkdownWriter(Layout layout) {
			this.layout = layout;
		}

		void newPage() {
			pageBreak = previous != null;
		}

		void add(PdfLine line) {
			int level = layout.headingLevel(line);
			String item = listItem(line);
			if (level > 0) {
				addHeadingLine(line, level);
			} else if (item != null) {
				addListItem(line, item);
			} else if (block == Block.LIST && sameBlock(line) && line.x() > lastItemX + INDENT_TOLERANCE) {
				// Indented past the item's marker: the item's text wrapped onto another line.
				join(line.text(), " ");
			} else if (block == Block.PARAGRAPH && (sameBlock(line) || continuesAfterBreak(line))) {
				join(line.text(), "\n");
			} else {
				start(Block.PARAGRAPH);
				listIndents.clear();
				current.add(line.text());
			}
			previous = line;
			pageBreak = false;
		}

		/**
		 * Writes rows of cells as a Markdown table whose first row is the header. A row with a cell for every column
		 * fills them in order. A row with fewer cells puts each one in the nearest column, by the cell positions of the
		 * last row with the most cells, and joins cells that land in the same column.
		 */
		void addTable(List<List<PdfLine>> rows) {
			start(Block.TABLE);
			listIndents.clear();
			// The last row with the most cells: header cells are often centered, data cells start at the column edge.
			List<Float> columns = rows.stream()
					.reduce((widest, row) -> row.size() >= widest.size() ? row : widest)
					.orElseThrow()
					.stream()
					.map(PdfLine::x)
					.toList();
			for (List<PdfLine> row : rows) {
				current.add(tableRow(row, columns));
				if (current.size() == 1) {
					current.add("|" + " --- |".repeat(columns.size()));
				}
			}
			previous = rows.getLast().getFirst();
			pageBreak = false;
		}

		String finish() {
			flush();
			return blocks.isEmpty() ? "" : String.join("\n\n", blocks) + "\n";
		}

		/** A heading wrapped over several lines becomes one heading. */
		private void addHeadingLine(PdfLine line, int level) {
			if (block == Block.HEADING && layout.headingLevel(previous) == level && sameBlock(line)) {
				join(line.text(), " ");
				return;
			}
			start(Block.HEADING);
			listIndents.clear();
			current.add("#".repeat(level) + " " + line.text());
		}

		/**
		 * Nesting follows indentation relative to the open list's earlier items, so it survives page and column breaks.
		 * Items separated by a large gap stay in the same list but start a new block.
		 */
		private void addListItem(PdfLine line, String item) {
			if (block != Block.LIST || !sameBlock(line)) {
				start(Block.LIST);
			}
			while (!listIndents.isEmpty() && line.x() < listIndents.getLast() - INDENT_TOLERANCE) {
				listIndents.removeLast();
			}
			if (listIndents.isEmpty() || line.x() > listIndents.getLast() + INDENT_TOLERANCE) {
				listIndents.add(line.x());
			}
			current.add(LIST_INDENT.repeat(listIndents.size() - 1) + item);
			lastItemX = line.x();
		}

		private static String tableRow(List<PdfLine> row, List<Float> columns) {
			String[] cells = new String[columns.size()];
			Arrays.fill(cells, "");
			for (int i = 0; i < row.size(); i++) {
				PdfLine cell = row.get(i);
				int column = row.size() == columns.size() ? i : nearestColumn(cell.x(), columns);
				String text = cell.text().replace("|", "\\|");
				cells[column] = cells[column].isEmpty() ? text : cells[column] + " " + text;
			}
			return "| " + String.join(" | ", cells) + " |";
		}

		private static int nearestColumn(float x, List<Float> columns) {
			int nearest = 0;
			for (int i = 1; i < columns.size(); i++) {
				if (Math.abs(columns.get(i) - x) < Math.abs(columns.get(nearest) - x)) {
					nearest = i;
				}
			}
			return nearest;
		}

		/** Directly below the previous line on the same page, with no larger gap than ordinary line spacing. */
		private boolean sameBlock(PdfLine line) {
			float gap = line.y() - previous.y();
			return !pageBreak && gap >= 0 && gap <= BLOCK_GAP * Math.max(previous.fontSize(), line.fontSize());
		}

		/**
		 * After a page break, or a column break (the text moved back up the page), a line that starts in lowercase
		 * continues the paragraph before the break.
		 */
		private boolean continuesAfterBreak(PdfLine line) {
			return (pageBreak || line.y() < previous.y()) && Character.isLowerCase(line.text().codePointAt(0));
		}

		/**
		 * Adds text to the block's last line, or as a new line when the separator is a newline. A word hyphenated
		 * across the line break is rejoined without the hyphen.
		 */
		private void join(String text, String separator) {
			String last = current.getLast();
			if (LINE_END_HYPHEN.matcher(last).find() && Character.isLowerCase(text.codePointAt(0))) {
				current.set(current.size() - 1, last.substring(0, last.length() - 1) + text);
			} else if (separator.equals("\n")) {
				current.add(text);
			} else {
				current.set(current.size() - 1, last + separator + text);
			}
		}

		private void start(Block next) {
			flush();
			block = next;
		}

		private void flush() {
			if (!current.isEmpty()) {
				blocks.add(String.join("\n", current));
				current.clear();
			}
		}

	}

	/**
	 * The document's body text size and the heading level of each larger size. Body size is the size that holds the
	 * most characters. Heading sizes are ranked largest first; the third size and anything smaller share level 3.
	 */
	private record Layout(float bodySize, Map<Float, Integer> headingLevels) {

		static Layout of(List<PdfLine> lines) {
			Map<Float, Integer> characters = new HashMap<>();
			lines.forEach(line -> characters.merge(line.fontSize(), line.text().length(), Integer::sum));
			float bodySize = characters.entrySet()
					.stream()
					.max(Map.Entry.comparingByValue())
					.map(Map.Entry::getKey)
					.orElse(0f);
			List<Float> sizes = lines.stream()
					.filter(line -> isHeadingCandidate(line, bodySize))
					.map(PdfLine::fontSize)
					.distinct()
					.sorted((a, b) -> Float.compare(b, a))
					.toList();
			Map<Float, Integer> levels = new HashMap<>();
			for (int i = 0; i < sizes.size(); i++) {
				levels.put(sizes.get(i), Math.min(i + 1, MAX_HEADING_LEVEL));
			}
			return new Layout(bodySize, levels);
		}

		/** 0 for body text, otherwise 1 to 3. */
		int headingLevel(PdfLine line) {
			return isHeadingCandidate(line, bodySize) ? headingLevels.get(line.fontSize()) : 0;
		}

		/**
		 * Text only slightly larger than the body must also be bold, which keeps a LaTeX title page's author and date
		 * out. Bold text at body size is never a heading: it is indistinguishable from a bold table header row or a
		 * bold lead-in sentence.
		 */
		private static boolean isHeadingCandidate(PdfLine line, float bodySize) {
			boolean larger = line.fontSize() >= bodySize * HEADING_SIZE_RATIO
					|| line.bold() && line.fontSize() >= bodySize + BOLD_HEADING_SIZE_MARGIN;
			return larger && line.text().length() <= MAX_HEADING_LENGTH && !line.text().endsWith(".")
					&& listItem(line) == null;
		}

	}

}

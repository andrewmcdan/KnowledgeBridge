package com.capstone.knowledgebridge.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * Converts a .docx document body to Markdown so it can be ingested like a Markdown upload: heading styles become
 * headings, numbered and bulleted paragraphs become lists, and tables become Markdown tables. Run formatting, images,
 * comments, headers, footers, and footnotes are left out. See the package README for the full rules.
 */
@Component
public class DocxMarkdownConverter {

	private static final Pattern HEADING_STYLE = Pattern.compile("heading ([1-9])");

	private static final int MAX_HEADING_LEVEL = 6;

	private static final String LIST_INDENT = "    ";

	/**
	 * Returns the document body as Markdown, or an empty string when the body has no text. Throws ExtractionException
	 * when the stream isn't a readable .docx; POI reports corrupt, encrypted, and non-OOXML input with a mix of
	 * IOExceptions and runtime exceptions, so both are translated.
	 */
	public String convert(InputStream docx) {
		try (XWPFDocument document = new XWPFDocument(docx)) {
			return toMarkdown(document);
		} catch (IOException | RuntimeException ex) {
			throw new ExtractionException("Could not read the file as a .docx document", ex);
		}
	}

	private static String toMarkdown(XWPFDocument document) {
		List<String> blocks = new ArrayList<>();
		StringBuilder list = new StringBuilder();
		for (IBodyElement element : document.getBodyElements()) {
			if (element instanceof XWPFParagraph paragraph && isListItem(paragraph)) {
				String item = listItem(paragraph);
				if (item != null) {
					list.append(item).append('\n');
				}
				continue;
			}
			flushList(list, blocks);
			// POI's body elements are paragraphs, tables, and content controls (XWPFSDT); nothing else.
			String block;
			if (element instanceof XWPFParagraph paragraph) {
				block = paragraph(paragraph, document.getStyles());
			} else if (element instanceof XWPFTable table) {
				block = table(table);
			} else {
				block = text(((XWPFSDT) element).getContent().getText());
			}
			if (block != null) {
				blocks.add(block);
			}
		}
		flushList(list, blocks);
		return blocks.isEmpty() ? "" : String.join("\n\n", blocks) + "\n";
	}

	private static void flushList(StringBuilder list, List<String> blocks) {
		if (!list.isEmpty()) {
			blocks.add(list.toString().stripTrailing());
			list.setLength(0);
		}
	}

	private static String paragraph(XWPFParagraph paragraph, XWPFStyles styles) {
		String text = text(paragraphText(paragraph));
		if (text == null) {
			return null;
		}
		int level = headingLevel(paragraph, styles);
		return level == 0 ? text : "#".repeat(level) + " " + singleLine(text);
	}

	private static boolean isListItem(XWPFParagraph paragraph) {
		return paragraph.getNumID() != null && headingLevel(paragraph, paragraph.getDocument().getStyles()) == 0;
	}

	private static String listItem(XWPFParagraph paragraph) {
		String text = text(paragraphText(paragraph));
		if (text == null) {
			return null;
		}
		BigInteger level = paragraph.getNumIlvl();
		String indent = LIST_INDENT.repeat(level == null ? 0 : level.intValue());
		String marker = "bullet".equals(paragraph.getNumFmt()) || paragraph.getNumFmt() == null ? "- " : "1. ";
		return indent + marker + singleLine(text);
	}

	/** 1-6 for Title and Heading styles, 0 for anything else. Matched by style name because Word localizes IDs. */
	private static int headingLevel(XWPFParagraph paragraph, XWPFStyles styles) {
		if (paragraph.getStyleID() == null || styles == null) {
			return 0;
		}
		XWPFStyle style = styles.getStyle(paragraph.getStyleID());
		if (style == null || style.getName() == null) {
			return 0;
		}
		String name = style.getName().toLowerCase(Locale.ROOT);
		if (name.equals("title")) {
			return 1;
		}
		Matcher heading = HEADING_STYLE.matcher(name);
		return heading.matches() ? Math.min(Integer.parseInt(heading.group(1)), MAX_HEADING_LEVEL) : 0;
	}

	private static String table(XWPFTable table) {
		List<List<String>> rows = new ArrayList<>();
		int columns = 0;
		for (XWPFTableRow row : table.getRows()) {
			List<String> cells = new ArrayList<>();
			for (XWPFTableCell cell : row.getTableCells()) {
				cells.add(cell(cell));
				// A horizontally merged cell spans several grid columns; pad so later cells stay aligned.
				BigInteger span = cell.getCTTc().getTcPr() == null || cell.getCTTc().getTcPr().getGridSpan() == null
						? BigInteger.ONE
						: cell.getCTTc().getTcPr().getGridSpan().getVal();
				for (int i = 1; i < span.intValue(); i++) {
					cells.add("");
				}
			}
			columns = Math.max(columns, cells.size());
			rows.add(cells);
		}
		if (columns == 0) {
			return null;
		}
		StringBuilder markdown = new StringBuilder();
		for (int i = 0; i < rows.size(); i++) {
			List<String> cells = rows.get(i);
			while (cells.size() < columns) {
				cells.add("");
			}
			markdown.append("| ").append(String.join(" | ", cells)).append(" |\n");
			if (i == 0) {
				markdown.append("|").append(" --- |".repeat(columns)).append('\n');
			}
		}
		return markdown.toString().stripTrailing();
	}

	private static String cell(XWPFTableCell cell) {
		return singleLine(cellText(cell).strip()).replace("|", "\\|");
	}

	// Cell paragraphs on separate lines (joined later by singleLine); a nested table is flattened into the cell text.
	private static String cellText(XWPFTableCell cell) {
		List<String> parts = new ArrayList<>();
		for (IBodyElement element : cell.getBodyElements()) {
			if (element instanceof XWPFParagraph paragraph) {
				parts.add(paragraphText(paragraph));
			} else if (element instanceof XWPFTable nested) {
				for (XWPFTableRow row : nested.getRows()) {
					for (XWPFTableCell nestedCell : row.getTableCells()) {
						parts.add(cellText(nestedCell));
					}
				}
			} else {
				parts.add(((XWPFSDT) element).getContent().getText());
			}
		}
		return String.join("\n", parts);
	}

	/**
	 * The paragraph's visible text. XWPFParagraph.getText() is not used because it appends footnote and endnote text
	 * (and "[footnoteRef:1]" markers) to the paragraph, and notes are skipped.
	 */
	private static String paragraphText(XWPFParagraph paragraph) {
		StringBuilder text = new StringBuilder();
		for (IRunElement element : paragraph.getIRuns()) {
			// A paragraph's run elements are runs (including hyperlink and field runs) and inline content controls.
			if (element instanceof XWPFRun run) {
				if (!isNoteReference(run)) {
					text.append(run.text());
				}
			} else {
				text.append(((XWPFSDT) element).getContent().getText());
			}
		}
		return text.toString();
	}

	private static boolean isNoteReference(XWPFRun run) {
		return run.getCTR().sizeOfFootnoteReferenceArray() > 0 || run.getCTR().sizeOfEndnoteReferenceArray() > 0;
	}

	/** Stripped paragraph text, or null when there is none so blank paragraphs are dropped. */
	private static String text(String raw) {
		String text = raw.strip();
		return text.isEmpty() ? null : text;
	}

	private static String singleLine(String text) {
		return text.replaceAll("\\s*\\R\\s*", " ");
	}

}

package com.capstone.knowledgebridge.extraction;

import static com.capstone.knowledgebridge.extraction.DocxFixtures.docx;
import static com.capstone.knowledgebridge.extraction.DocxFixtures.list;
import static com.capstone.knowledgebridge.extraction.DocxFixtures.listItem;
import static com.capstone.knowledgebridge.extraction.DocxFixtures.paragraph;
import static com.capstone.knowledgebridge.extraction.DocxFixtures.style;
import static com.capstone.knowledgebridge.extraction.DocxFixtures.styled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFEndnote;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

class DocxMarkdownConverterTests {

	private final DocxMarkdownConverter converter = new DocxMarkdownConverter();

	private String convert(byte[] docx) {
		return converter.convert(new ByteArrayInputStream(docx));
	}

	@Test
	void titleAndHeadingStylesBecomeMarkdownHeadings() {
		byte[] docx = docx(document -> {
			style(document, "Title", "Title");
			style(document, "Heading1", "heading 1");
			style(document, "Heading2", "Heading 2");
			style(document, "Heading9", "heading 9");
			styled(document, "Title", "Vendor Onboarding");
			styled(document, "Heading1", "Scope");
			styled(document, "Heading2", "Exceptions");
			styled(document, "Heading9", "Deepest");
		});

		assertThat(convert(docx)).isEqualTo("""
				# Vendor Onboarding

				# Scope

				## Exceptions

				###### Deepest
				""");
	}

	@Test
	void headingsAreMatchedByStyleNameNotLocalizedStyleId() {
		byte[] docx = docx(document -> {
			style(document, "berschrift1", "heading 1");
			styled(document, "berschrift1", "Geltungsbereich");
		});

		assertThat(convert(docx)).isEqualTo("# Geltungsbereich\n");
	}

	@Test
	void unknownUnnamedAndOtherStylesStayParagraphs() {
		byte[] docx = docx(document -> {
			style(document, "NoName", null);
			style(document, "Quote", "Quote");
			styled(document, "NoName", "Unnamed style");
			styled(document, "Quote", "Quoted text");
			styled(document, "Missing", "Style not defined");
		});

		assertThat(convert(docx)).isEqualTo("""
				Unnamed style

				Quoted text

				Style not defined
				""");
	}

	@Test
	void styledParagraphInADocumentWithoutStylesIsAParagraph() {
		byte[] docx = docx(document -> styled(document, "Heading1", "No styles part"));

		assertThat(convert(docx)).isEqualTo("No styles part\n");
	}

	@Test
	void blankParagraphsAreDroppedAndLineBreaksKept() {
		byte[] docx = docx(document -> {
			style(document, "Heading1", "heading 1");
			paragraph(document, "First paragraph.");
			paragraph(document, "   ");
			document.createParagraph();
			XWPFRun run = paragraph(document, "Line one").getRuns().get(0);
			run.addBreak();
			run.setText("Line two");
			XWPFRun heading = styled(document, "Heading1", "Split").getRuns().get(0);
			heading.addBreak();
			heading.setText("heading");
		});

		assertThat(convert(docx)).isEqualTo("""
				First paragraph.

				Line one
				Line two

				# Split heading
				""");
	}

	@Test
	void bulletedAndNumberedListsKeepTheirNesting() {
		byte[] docx = docx(document -> {
			BigInteger bullets = list(document, STNumberFormat.BULLET);
			BigInteger numbers = list(document, STNumberFormat.DECIMAL);
			paragraph(document, "Steps:");
			listItem(document, numbers, 0, "Open a request");
			listItem(document, bullets, 1, "Attach approval");
			listItem(document, numbers, 0, " ");
			listItem(document, numbers, 0, "Close the request");
			paragraph(document, "Done.");
		});

		assertThat(convert(docx)).isEqualTo("""
				Steps:

				1. Open a request
				    - Attach approval
				1. Close the request

				Done.
				""");
	}

	@Test
	void listItemsWithoutALevelOrKnownFormatBecomeTopLevelBullets() {
		byte[] docx = docx(document -> {
			XWPFParagraph noLevel = paragraph(document, "No level");
			noLevel.setNumID(BigInteger.valueOf(42));
			XWPFRun run = listItem(document, BigInteger.valueOf(42), 0, "Wrapped").getRuns().get(0);
			run.addBreak();
			run.setText("item");
		});

		assertThat(convert(docx)).isEqualTo("""
				- No level
				- Wrapped item
				""");
	}

	@Test
	void numberedHeadingsStayHeadings() {
		byte[] docx = docx(document -> {
			style(document, "Heading2", "heading 2");
			BigInteger numbers = list(document, STNumberFormat.DECIMAL);
			listItem(document, numbers, 0, "Numbered section").setStyle("Heading2");
		});

		assertThat(convert(docx)).isEqualTo("## Numbered section\n");
	}

	@Test
	void tablesBecomeMarkdownTablesWithAHeaderRow() {
		byte[] docx = docx(document -> {
			paragraph(document, "Before the table.");
			XWPFTable table = document.createTable(4, 3);
			setRow(table.getRow(0), "Situation", "Mode", "Contact");
			setRow(table.getRow(1), "Feed down", "Webhook | Batch", "");
			XWPFTableCell twoLines = table.getRow(1).getCell(2);
			twoLines.setText("Harbor");
			twoLines.addParagraph().createRun().setText("on-call");
			XWPFTableRow merged = table.getRow(2);
			setRow(merged, "Data exposure", "", "Security");
			merged.getCell(0).getCTTc().addNewTcPr().addNewGridSpan().setVal(BigInteger.TWO);
			merged.removeCell(1);
			XWPFTableRow shortRow = table.getRow(3);
			setRow(shortRow, "Short row", "", "");
			shortRow.removeCell(2);
			shortRow.removeCell(1);
			paragraph(document, "After the table.");
		});

		assertThat(convert(docx)).isEqualTo("""
				Before the table.

				| Situation | Mode | Contact |
				| --- | --- | --- |
				| Feed down | Webhook \\| Batch | Harbor on-call |
				| Data exposure |  | Security |
				| Short row |  |  |

				After the table.
				""");
	}

	@Test
	void cellPropertiesWithoutASpanCoverOneColumn() {
		byte[] docx = docx(document -> {
			XWPFTable table = document.createTable(1, 2);
			setRow(table.getRow(0), "Width only", "Next");
			table.getRow(0).getCell(0).getCTTc().addNewTcPr().addNewTcW().setW(BigInteger.valueOf(2000));
		});

		assertThat(convert(docx)).isEqualTo("""
				| Width only | Next |
				| --- | --- |
				""");
	}

	@Test
	void nestedTablesAndContentControlsInACellAreFlattenedIntoItsText() {
		byte[] docx = docx(document -> {
			XWPFTableCell cell = document.createTable(1, 1).getRow(0).getCell(0);
			cell.setText("Outer");
			CTRow nestedRow = cell.getCTTc().addNewTbl().addNewTr();
			nestedRow.addNewTc().addNewP().addNewR().addNewT().setStringValue("Inner A");
			nestedRow.addNewTc().addNewP().addNewR().addNewT().setStringValue("Inner B");
			cell.getCTTc().addNewSdt().addNewSdtContent().addNewP().addNewR().addNewT().setStringValue("Control");
		});

		assertThat(convert(docx)).contains("| Outer Inner A Inner B Control |");
	}

	@Test
	void inlineContentControlTextIsPartOfTheParagraph() {
		byte[] docx = docx(document -> {
			XWPFParagraph paragraph = paragraph(document, "Owner: ");
			paragraph.getCTP().addNewSdt().addNewSdtContent().addNewR().addNewT().setStringValue("Nora Ellis");
		});

		assertThat(convert(docx)).isEqualTo("Owner: Nora Ellis\n");
	}

	@Test
	void tableWithoutCellsIsDropped() {
		byte[] docx = docx(document -> {
			document.createTable().removeRow(0);
			paragraph(document, "Only text");
		});

		assertThat(convert(docx)).isEqualTo("Only text\n");
	}

	@Test
	void contentControlTextIsKeptAndEmptyOnesAreDropped() {
		byte[] docx = docx(document -> {
			CTBody body = document.getDocument().getBody();
			body.addNewSdt().addNewSdtContent().addNewP().addNewR().addNewT().setStringValue("Inside a control");
			body.addNewSdt().addNewSdtContent();
		});

		assertThat(convert(docx)).isEqualTo("Inside a control\n");
	}

	@Test
	void headersFootersFootnotesAndCommentsAreSkipped() {
		byte[] docx = docx(document -> {
			document.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("HEADER TEXT");
			document.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("FOOTER TEXT");
			XWPFParagraph body = paragraph(document, "Body text.");

			document.createFootnotes();
			XWPFFootnote footnote = document.createFootnote();
			footnote.createParagraph().createRun().setText("FOOTNOTE TEXT");
			body.addFootnoteReference(footnote);

			document.createEndnotes();
			XWPFEndnote endnote = document.createEndnote();
			endnote.createParagraph().createRun().setText("ENDNOTE TEXT");
			body.addFootnoteReference(endnote);

			XWPFComment comment = document.createComments().createComment(BigInteger.ONE);
			comment.setAuthor("Reviewer");
			comment.createParagraph().createRun().setText("COMMENT TEXT");
			CTP ctp = body.getCTP();
			ctp.addNewCommentRangeStart().setId(BigInteger.ONE);
			ctp.addNewCommentRangeEnd().setId(BigInteger.ONE);
			ctp.addNewR().addNewCommentReference().setId(BigInteger.ONE);
		});

		assertThat(convert(docx)).isEqualTo("Body text.\n");
	}

	@Test
	void documentWithoutTextConvertsToAnEmptyString() {
		assertThat(convert(docx(document -> document.createParagraph()))).isEmpty();
	}

	@Test
	void bytesThatAreNotAZipAreRejected() {
		byte[] notDocx = "# Just Markdown".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> convert(notDocx)).isInstanceOf(ExtractionException.class)
				.hasMessageContaining(".docx")
				.hasCauseInstanceOf(Exception.class);
	}

	@Test
	void zipWithoutAWordDocumentIsRejected() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("notes.txt"));
			zip.write("not a document".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}

		assertThatThrownBy(() -> convert(bytes.toByteArray())).isInstanceOf(ExtractionException.class);
	}

	private static void setRow(XWPFTableRow row, String... values) {
		for (int i = 0; i < values.length; i++) {
			row.getCell(i).setText(values[i]);
		}
	}

}

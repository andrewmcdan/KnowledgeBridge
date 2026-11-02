package com.capstone.knowledgebridge.extraction.pdf;

import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.encryptedPdf;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.formPage;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.imagePage;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.nestedImagePage;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.page;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.pdf;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.rotated;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.row;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.extraction.ExtractionException;
import com.capstone.knowledgebridge.extraction.ExtractionException.Reason;

class PdfTextExtractorTests {

	private final PdfTextExtractor extractor = new PdfTextExtractor();

	private List<PdfPage> extract(byte[] pdf) {
		return extractor.extract(new ByteArrayInputStream(pdf));
	}

	@Test
	void linesKeepTheirTextFontSizeBoldnessAndPosition() {
		byte[] pdf = pdf(page(text("Vendor Onboarding", 20, true, 72), text("Scope and goals", 11, false, 110)));

		assertThat(extract(pdf)).containsExactly(new PdfPage(1,
				List.of(new PdfLine("Vendor Onboarding", 20, true, 72, 72),
						new PdfLine("Scope and goals", 11, false, 72, 110)),
				792, 0));
	}

	@Test
	void indentedLinesKeepTheirLeftEdge() {
		byte[] pdf = pdf(page(new PdfFixtures.Text("1. Check the feed", 11, false, 72, 100),
				new PdfFixtures.Text("a. Webhook feeds", 11, false, 90, 114)));

		assertThat(extract(pdf).getFirst().lines()).extracting(PdfLine::x).containsExactly(72f, 90f);
	}

	@Test
	void runsWithSpacesInThemSplitIntoCellsAtAnyGap() {
		byte[] pdf = pdf(page(row(11, 100, 4, "Duplicate records delivered", "Batch", "Morgan Reed")));

		List<PdfLine> cells = extract(pdf).getFirst().lines();

		assertThat(cells).extracting(PdfLine::text).containsExactly("Duplicate records delivered", "Batch",
				"Morgan Reed");
		assertThat(cells).extracting(PdfLine::y).containsOnly(100f);
		assertThat(cells.get(1).x()).isGreaterThan(cells.get(0).x());
	}

	@Test
	void aRunOfOnlySpacesBetweenCellsIsNotACell() {
		byte[] pdf = pdf(page(row(11, 100, 4, "Owner name", "      ", "Due date")));

		assertThat(extract(pdf).getFirst().lines()).extracting(PdfLine::text).containsExactly("Owner name", "Due date");
	}

	@Test
	void singleWordRunsSplitOnlyAtAGapOfAboutAFontSize() {
		byte[] pdf = pdf(page(row(11, 100, 3, "Situation", "Owner")), page(row(11, 100, 12, "Situation", "Owner")));

		List<PdfPage> pages = extract(pdf);

		assertThat(pages.get(0).lines()).extracting(PdfLine::text).containsExactly("Situation Owner");
		assertThat(pages.get(1).lines()).extracting(PdfLine::text).containsExactly("Situation", "Owner");
	}

	@Test
	void theFontMostCharactersUseDecidesBoldness() throws IOException {
		float boldWidth = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD).getStringWidth("Note:") / 1000 * 11;
		byte[] pdf = pdf(page(new PdfFixtures.Text("Note:", 11, true, 72, 100),
				new PdfFixtures.Text("vendors", 11, false, 72 + boldWidth + 3, 100)));

		PdfLine line = extract(pdf).getFirst().lines().getFirst();

		assertThat(line.text()).isEqualTo("Note: vendors");
		assertThat(line.bold()).isFalse();
	}

	@Test
	void everyPageIsReturnedInOrderIncludingPagesWithoutText() {
		byte[] pdf = pdf(page(text("First", 11, false, 72)), page(), page(text("Third", 11, false, 72)));

		List<PdfPage> pages = extract(pdf);

		assertThat(pages).extracting(PdfPage::number).containsExactly(1, 2, 3);
		assertThat(pages).extracting(page -> page.lines().size()).containsExactly(1, 0, 1);
		assertThat(pages.get(2).lines().getFirst().text()).isEqualTo("Third");
	}

	@Test
	void imagesAreCountedPerPage() {
		byte[] pdf = pdf(imagePage(), imagePage(text("Stamped page 2", 9, false, 750)),
				page(text("Plain", 11, false, 72)));

		assertThat(extract(pdf)).extracting(PdfPage::imageCount).containsExactly(1, 1, 0);
	}

	@Test
	void rotatedPageKeepsWholeLinesAndReportsItsDisplayedHeight() {
		byte[] pdf = rotated(pdf(page(text("Office Equipment Request Guide", 24, true, 72),
				text("Owner: Nora Ellis, People Operations.", 11, false, 110))), 90);

		PdfPage page = extract(pdf).getFirst();

		assertThat(page.lines()).extracting(PdfLine::text)
				.containsExactly("Office Equipment Request Guide", "Owner: Nora Ellis, People Operations.");
		assertThat(page.height()).isEqualTo(612);
	}

	@Test
	void upsideDownPageKeepsItsHeight() {
		byte[] pdf = rotated(pdf(page(text("Upside down", 11, false, 72))), 180);

		PdfPage page = extract(pdf).getFirst();

		assertThat(page.lines()).extracting(PdfLine::text).containsExactly("Upside down");
		assertThat(page.height()).isEqualTo(792);
	}

	@Test
	void formXObjectsAreNotCountedAsImages() {
		byte[] pdf = pdf(formPage(text("Drawn over a form", 11, false, 72)));

		assertThat(extract(pdf).getFirst().imageCount()).isZero();
	}

	@Test
	void imagesInsideFormsAreCountedOnceEvenWhenAFormRefersToItself() {
		byte[] pdf = pdf(nestedImagePage(text("Scanned page wrapped in a form", 11, false, 72)));

		assertThat(extract(pdf).getFirst().imageCount()).isEqualTo(1);
	}

	@Test
	void passwordProtectedPdfIsRejected() {
		byte[] pdf = encryptedPdf("secret", page(text("Hidden", 11, false, 72)));

		assertThatThrownBy(() -> extract(pdf)).hasCauseInstanceOf(InvalidPasswordException.class)
				.isInstanceOfSatisfying(ExtractionException.class,
						ex -> assertThat(ex.reason()).isEqualTo(Reason.PASSWORD_PROTECTED));
	}

	@Test
	void nonPdfInputIsRejected() {
		byte[] notPdf = "# Just Markdown".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> extract(notPdf)).isInstanceOfSatisfying(ExtractionException.class,
				ex -> assertThat(ex.reason()).isEqualTo(Reason.UNREADABLE));
	}

}

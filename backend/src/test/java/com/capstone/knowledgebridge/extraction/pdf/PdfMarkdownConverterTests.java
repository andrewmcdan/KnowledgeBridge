package com.capstone.knowledgebridge.extraction.pdf;

import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.imagePage;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.page;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.pdf;
import static com.capstone.knowledgebridge.extraction.pdf.PdfFixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.extraction.ExtractionException;
import com.capstone.knowledgebridge.extraction.ExtractionException.Reason;

class PdfMarkdownConverterTests {

	private final PdfMarkdownConverter converter = new PdfMarkdownConverter(new PdfTextExtractor());

	private String convert(byte[] pdf) {
		return converter.convert(new ByteArrayInputStream(pdf));
	}

	@Test
	void pdfBecomesMarkdownWithHeadingsParagraphsAndLists() {
		byte[] pdf = pdf(page(text("Vendor Onboarding", 20, true, 72),
				text("Every vendor completes onboarding before the first purchase order.", 11, false, 110),
				text("Finance reviews the tax forms within five business days.", 11, false, 124),
				text("• Collect the W-9 form", 11, false, 160), text("• Verify the bank account", 11, false, 174)));

		assertThat(convert(pdf)).isEqualTo("""
				# Vendor Onboarding

				Every vendor completes onboarding before the first purchase order.
				Finance reviews the tax forms within five business days.

				- Collect the W-9 form
				- Verify the bank account
				""");
	}

	@Test
	void scannedPdfIsRejectedBeforeConversion() {
		assertThatThrownBy(() -> convert(pdf(imagePage()))).isInstanceOfSatisfying(ExtractionException.class,
				ex -> assertThat(ex.reason()).isEqualTo(Reason.PDF_NO_SELECTABLE_TEXT));
	}

	@Test
	void unreadableInputIsRejected() {
		byte[] notPdf = "not a pdf".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> convert(notPdf)).isInstanceOf(ExtractionException.class);
	}

}

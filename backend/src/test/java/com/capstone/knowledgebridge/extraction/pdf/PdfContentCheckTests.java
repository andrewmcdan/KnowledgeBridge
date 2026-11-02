package com.capstone.knowledgebridge.extraction.pdf;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.extraction.ExtractionException;
import com.capstone.knowledgebridge.extraction.ExtractionException.Reason;

class PdfContentCheckTests {

	private static final String TEXT = "Every vendor completes onboarding first.";

	private static PdfPage textPage() {
		return new PdfPage(1, List.of(new PdfLine(TEXT, 11, false, 72, 100)), 792, 0);
	}

	/** An image with at most a stamped page number: under 20 characters once whitespace is ignored. */
	private static PdfPage scannedPage() {
		return new PdfPage(1, List.of(new PdfLine("Page   12   of   40   ok", 9, false, 72, 780)), 792, 1);
	}

	private static PdfPage blankPage() {
		return new PdfPage(1, List.of(), 792, 0);
	}

	private static List<PdfPage> pages(int text, int scanned, int blank) {
		List<PdfPage> pages = new ArrayList<>();
		for (int i = 0; i < text; i++) {
			pages.add(textPage());
		}
		for (int i = 0; i < scanned; i++) {
			pages.add(scannedPage());
		}
		for (int i = 0; i < blank; i++) {
			pages.add(blankPage());
		}
		return pages;
	}

	private static void assertRejected(List<PdfPage> pages, Reason reason) {
		assertThatThrownBy(() -> PdfContentCheck.check(pages)).isInstanceOfSatisfying(ExtractionException.class,
				ex -> org.assertj.core.api.Assertions.assertThat(ex.reason()).isEqualTo(reason));
	}

	@Test
	void pdfWithoutAnyTextIsRejectedWhetherScannedOrOutlined() {
		assertRejected(pages(0, 2, 0), Reason.PDF_NO_SELECTABLE_TEXT);
		// Text converted to vector outlines: no text and no images.
		assertRejected(pages(0, 0, 3), Reason.PDF_NO_SELECTABLE_TEXT);
	}

	@Test
	void pdfWithMoreScannedPagesThanTextPagesIsRejected() {
		assertRejected(pages(1, 2, 0), Reason.PDF_MOSTLY_SCANNED);
	}

	@Test
	void aFewScannedPagesAndBlankPagesAreAllowed() {
		assertThatCode(() -> PdfContentCheck.check(pages(2, 2, 0))).doesNotThrowAnyException();
		assertThatCode(() -> PdfContentCheck.check(pages(1, 1, 5))).doesNotThrowAnyException();
	}

}

package com.capstone.knowledgebridge.extraction.pdf;

import java.util.List;

import com.capstone.knowledgebridge.extraction.ExtractionException;
import com.capstone.knowledgebridge.extraction.ExtractionException.Reason;

/**
 * Rejects PDFs whose text can't be read from the text layer. The decision rests on how much text each page has, not on
 * whether it has images: a PDF whose text was converted to vector outlines has neither text nor images, and a page of
 * text can carry a logo. Images only tell a scanned page apart from an intentionally blank one.
 */
final class PdfContentCheck {

	/** A page with fewer non-whitespace characters has no usable text; this ignores a stamped page number. */
	static final int MIN_PAGE_CHARACTERS = 20;

	private PdfContentCheck() {
	}

	/**
	 * Throws PDF_NO_SELECTABLE_TEXT when no page has text, and PDF_MOSTLY_SCANNED when more than half of the pages that
	 * aren't blank are scanned: an image with no usable text. A few scanned pages, such as a signed last page, are
	 * allowed and contribute nothing.
	 */
	static void check(List<PdfPage> pages) {
		int textPages = 0;
		int scannedPages = 0;
		for (PdfPage page : pages) {
			if (characters(page) >= MIN_PAGE_CHARACTERS) {
				textPages++;
			} else if (page.imageCount() > 0) {
				scannedPages++;
			}
		}
		if (textPages == 0) {
			throw new ExtractionException(Reason.PDF_NO_SELECTABLE_TEXT);
		}
		if (scannedPages > textPages) {
			throw new ExtractionException(Reason.PDF_MOSTLY_SCANNED);
		}
	}

	private static int characters(PdfPage page) {
		return page.lines().stream()
				.mapToInt(line -> (int) line.text().codePoints().filter(c -> !Character.isWhitespace(c)).count()).sum();
	}

}

package com.capstone.knowledgebridge.extraction.pdf;

import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Converts a PDF's text layer to Markdown so it can be ingested like a Markdown upload. Headings, paragraphs, and lists
 * are inferred from font size and layout, so the result is plainer than a .docx conversion. See the package README for
 * the rules and their known limitations.
 */
@Component
public class PdfMarkdownConverter {

	private final PdfTextExtractor extractor;

	public PdfMarkdownConverter(PdfTextExtractor extractor) {
		this.extractor = extractor;
	}

	/**
	 * Returns the document as Markdown. Throws ExtractionException when the stream isn't a readable PDF, needs a
	 * password, or has no selectable text on most of its pages (see PdfContentCheck).
	 */
	public String convert(InputStream pdf) {
		List<PdfPage> pages = extractor.extract(pdf);
		PdfContentCheck.check(pages);
		return PdfMarkdownMapper.toMarkdown(pages);
	}

}

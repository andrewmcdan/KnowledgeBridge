package com.capstone.knowledgebridge.extraction.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import com.capstone.knowledgebridge.extraction.ExtractionException;

/**
 * Reads a PDF's text layer page by page, keeping each line's font size, boldness, and position so later steps can infer
 * headings and detect scanned pages. This is the only class that touches PDFBox; it does no Markdown and makes no
 * accept or reject decisions.
 */
@Component
public class PdfTextExtractor {

	/**
	 * Returns every page of the document, including pages with no text. Throws ExtractionException when the stream
	 * isn't a readable PDF, including one that needs a password to open.
	 */
	public List<PdfPage> extract(InputStream pdf) {
		try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
			LineCollector collector = new LineCollector();
			List<PdfPage> pages = new ArrayList<>();
			// One page at a time, so pages without text still appear and keep their number.
			for (int number = 1; number <= document.getNumberOfPages(); number++) {
				PDPage page = document.getPage(number - 1);
				boolean sideways = page.getRotation() % 180 != 0;
				collector.setStartPage(number);
				collector.setEndPage(number);
				// Content-stream order keeps columns apart, but on a rotated page it splits text into fragments.
				collector.setSortByPosition(page.getRotation() != 0);
				collector.writeText(document, Writer.nullWriter());
				float height = sideways ? page.getCropBox().getWidth() : page.getCropBox().getHeight();
				pages.add(new PdfPage(number, collector.takeLines(), height,
						imageCount(page.getResources(), new HashSet<>())));
			}
			return pages;
		} catch (InvalidPasswordException ex) {
			throw new ExtractionException(ExtractionException.Reason.PASSWORD_PROTECTED, ex);
		} catch (IOException | RuntimeException ex) {
			throw new ExtractionException(ExtractionException.Reason.UNREADABLE, ex);
		}
	}

	/**
	 * Counts image XObjects in the resources, including images inside form XObjects (reusable drawings, as some
	 * scanners and PDF tools wrap a page image). Each form is searched once, which also stops a form that refers to
	 * itself.
	 */
	private static int imageCount(PDResources resources, Set<COSBase> searchedForms) throws IOException {
		if (resources == null) {
			return 0;
		}
		int count = 0;
		for (COSName name : resources.getXObjectNames()) {
			if (resources.isImageXObject(name)) {
				count++;
			} else if (resources.getXObject(name) instanceof PDFormXObject form
					&& searchedForms.add(form.getCOSObject())) {
				count += imageCount(form.getResources(), searchedForms);
			}
		}
		return count;
	}

	/**
	 * PDFTextStripper reports each line as words and separators; this collects them back into lines with the positions
	 * of their characters instead of writing plain text.
	 */
	private static final class LineCollector extends PDFTextStripper {

		private static final float CELL_GAP = 0.95f;

		private static final Pattern BOLD_FONT_NAME = Pattern.compile("bold|black|heavy|demi|bx\\d",
				Pattern.CASE_INSENSITIVE);

		private final List<PdfLine> lines = new ArrayList<>();

		/** The current line's words as PDFBox found them, with the positions of their characters. */
		private final List<String> words = new ArrayList<>();

		private final List<List<TextPosition>> wordPositions = new ArrayList<>();

		@Override
		protected void writeString(String string, List<TextPosition> textPositions) {
			// A word of only whitespace would make an empty cell; PDFBox reports spaces as word separators instead,
			// but a malformed PDF shouldn't produce a blank line.
			Optional.of(string).filter(Predicate.not(String::isBlank)).ifPresent(word -> {
				words.add(word);
				wordPositions.add(List.copyOf(textPositions));
			});
		}

		@Override
		protected void writeWordSeparator() {
			// Words are joined with a space when their cell is flushed.
		}

		@Override
		protected void writeLineSeparator() {
			flushLine();
		}

		List<PdfLine> takeLines() {
			flushLine();
			List<PdfLine> page = List.copyOf(lines);
			lines.clear();
			return page;
		}

		/** Adds the current line as one PdfLine per cell; see startsNewCell. */
		private void flushLine() {
			boolean spaced = words.stream().anyMatch(word -> word.strip().contains(" "));
			int start = 0;
			for (int end = 1; end <= words.size(); end++) {
				if (end == words.size() || spaced || wideGap(end)) {
					addCell(start, end);
					start = end;
				}
			}
			words.clear();
			wordPositions.clear();
		}

		/**
		 * PDFBox reports a gap between two runs of text as a word boundary. When the line's text contains real space
		 * characters, as LibreOffice, Word, and Chrome write it, such a gap is a table cell boundary or a tab stop.
		 * When it has none, as TeX writes it, every word boundary is a gap, so only a gap of about a font size or more
		 * separates cells: word spaces there stay under 0.85 of the font size, even after a sentence.
		 */
		private boolean wideGap(int word) {
			TextPosition previous = wordPositions.get(word - 1).getLast();
			TextPosition next = wordPositions.get(word).getFirst();
			float gap = next.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
			return gap >= CELL_GAP * next.getFontSizeInPt();
		}

		private void addCell(int from, int to) {
			String cell = String.join(" ", words.subList(from, to)).strip();
			List<TextPosition> positions = wordPositions.subList(from, to).stream().flatMap(List::stream).toList();
			TextPosition first = positions.getFirst();
			lines.add(new PdfLine(cell, fontSize(positions), bold(positions), first.getXDirAdj(), first.getYDirAdj()));
		}

		/** The size most characters use, rounded to half a point so tiny rendering differences don't split it. */
		private static float fontSize(List<TextPosition> positions) {
			Map<Float, Integer> counts = new HashMap<>();
			for (TextPosition position : positions) {
				counts.merge(Math.round(position.getFontSizeInPt() * 2) / 2f, 1, Integer::sum);
			}
			return counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
		}

		/**
		 * Boldness from the font name, because embedded fonts often leave the descriptor's weight and force-bold flag
		 * unset: "ABCDEF+Calibri-Bold", "Arial,Black", or TeX's bold extended fonts such as "CMBX12" and "SFBX1440".
		 */
		private static boolean bold(List<TextPosition> positions) {
			long boldCount = positions.stream()
					.filter(position -> BOLD_FONT_NAME.matcher(String.valueOf(position.getFont().getName())).find())
					.count();
			return boldCount * 2 > positions.size();
		}

	}

}

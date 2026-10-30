package com.capstone.knowledgebridge.extraction.pdf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.PDPostScriptXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Builds PDFs in memory with PDFBox, so tests need no committed binary fixtures. */
public final class PdfFixtures {

	private static final float PAGE_TOP = PDRectangle.LETTER.getHeight();

	private PdfFixtures() {
	}

	/** Text to draw on a page: y is measured down from the top of the page, like PdfLine.y. */
	public record Text(String text, float fontSize, boolean bold, float x, float y) {
	}

	/**
	 * What a page draws behind its text: nothing, a full-page image, an empty form XObject, or a form XObject holding
	 * an image and a reference to itself, next to a PostScript XObject.
	 */
	public enum Background {
		NONE, IMAGE, FORM, NESTED_IMAGE
	}

	/** A page's content: its background and the text runs to draw over it. */
	public record Page(Background background, Text... texts) {
	}

	public static Page page(Text... texts) {
		return new Page(Background.NONE, texts);
	}

	public static Page imagePage(Text... texts) {
		return new Page(Background.IMAGE, texts);
	}

	public static Page formPage(Text... texts) {
		return new Page(Background.FORM, texts);
	}

	public static Page nestedImagePage(Text... texts) {
		return new Page(Background.NESTED_IMAGE, texts);
	}

	/**
	 * Text runs on one baseline, left to right from x = 72, with the given gap in points between the end of one run and
	 * the start of the next, measured with Helvetica's glyph widths.
	 */
	public static Text[] row(float fontSize, float y, float gap, String... runs) {
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		Text[] texts = new Text[runs.length];
		float x = 72;
		try {
			for (int i = 0; i < runs.length; i++) {
				texts[i] = new Text(runs[i], fontSize, false, x, y);
				x += font.getStringWidth(runs[i]) / 1000 * fontSize + gap;
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return texts;
	}

	public static Text text(String text, float fontSize, boolean bold, float y) {
		return new Text(text, fontSize, bold, 72, y);
	}

	public static byte[] pdf(Page... pages) {
		return save(document(pages));
	}

	/** The same PDF with every page's /Rotate set, as a viewer's "rotate page" command saves it. */
	public static byte[] rotated(byte[] pdf, int degrees) {
		try {
			PDDocument document = Loader.loadPDF(pdf);
			document.getPages().forEach(page -> page.setRotation(degrees));
			return save(document);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A PDF that needs the given user password to open. */
	public static byte[] encryptedPdf(String userPassword, Page... pages) {
		PDDocument document = document(pages);
		try {
			StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", userPassword,
					new AccessPermission());
			document.protect(policy);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return save(document);
	}

	private static PDDocument document(Page... pages) {
		PDDocument document = new PDDocument();
		try {
			PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
			for (Page content : pages) {
				PDPage page = new PDPage(PDRectangle.LETTER);
				document.addPage(page);
				try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
					if (content.background() == Background.IMAGE) {
						PDImageXObject image = LosslessFactory.createFromImage(document,
								new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
						stream.drawImage(image, 0, 0, page.getMediaBox().getWidth(), PAGE_TOP);
					} else if (content.background() == Background.FORM) {
						PDFormXObject form = new PDFormXObject(document);
						form.setBBox(PDRectangle.LETTER);
						stream.drawForm(form);
					} else if (content.background() == Background.NESTED_IMAGE) {
						stream.drawForm(nestedImageForm(document));
					}
					for (Text text : content.texts()) {
						stream.beginText();
						stream.setFont(text.bold() ? bold : regular, text.fontSize());
						stream.newLineAtOffset(text.x(), PAGE_TOP - text.y());
						stream.showText(text.text());
						stream.endText();
					}
				}
			}
			return document;
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A form whose resources hold an image and the form itself, plus a PostScript XObject on the page. */
	private static PDFormXObject nestedImageForm(PDDocument document) throws IOException {
		PDFormXObject form = new PDFormXObject(document);
		form.setBBox(PDRectangle.LETTER);
		PDResources resources = new PDResources();
		resources.add(LosslessFactory.createFromImage(document, new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)));
		resources.add(form, "Fm");
		COSStream postScript = document.getDocument().createCOSStream();
		postScript.setItem(COSName.SUBTYPE, COSName.getPDFName("PS"));
		resources.put(COSName.getPDFName("PS0"), new PDPostScriptXObject(postScript));
		form.setResources(resources);
		return form;
	}

	private static byte[] save(PDDocument document) {
		try (document; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			document.save(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}

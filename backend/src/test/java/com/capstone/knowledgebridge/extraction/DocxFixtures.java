package com.capstone.knowledgebridge.extraction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.function.Consumer;

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

/** Builds .docx files in memory with POI, so tests need no committed binary fixtures. */
public final class DocxFixtures {

	private DocxFixtures() {
	}

	/** Serializes a document built by the given callback to .docx bytes. */
	public static byte[] docx(Consumer<XWPFDocument> build) {
		try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			build.accept(document);
			document.write(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Adds a paragraph style; pass a null name for a style with no name element. */
	public static void style(XWPFDocument document, String id, String name) {
		CTStyle style = CTStyle.Factory.newInstance();
		style.setStyleId(id);
		style.setType(STStyleType.PARAGRAPH);
		if (name != null) {
			style.addNewName().setVal(name);
		}
		document.createStyles().addStyle(new XWPFStyle(style));
	}

	/** Adds a two-level list definition and returns its numbering ID for XWPFParagraph.setNumID. */
	public static BigInteger list(XWPFDocument document, STNumberFormat.Enum format) {
		XWPFNumbering numbering = document.createNumbering();
		CTAbstractNum definition = CTAbstractNum.Factory.newInstance();
		BigInteger definitionId = BigInteger.valueOf(numbering.getAbstractNums().size() + 1L);
		definition.setAbstractNumId(definitionId);
		for (int level = 0; level < 2; level++) {
			CTLvl lvl = definition.addNewLvl();
			lvl.setIlvl(BigInteger.valueOf(level));
			lvl.addNewNumFmt().setVal(format);
		}
		return numbering.addNum(numbering.addAbstractNum(new XWPFAbstractNum(definition, numbering)));
	}

	public static XWPFParagraph paragraph(XWPFDocument document, String text) {
		XWPFParagraph paragraph = document.createParagraph();
		paragraph.createRun().setText(text);
		return paragraph;
	}

	public static XWPFParagraph styled(XWPFDocument document, String styleId, String text) {
		XWPFParagraph paragraph = paragraph(document, text);
		paragraph.setStyle(styleId);
		return paragraph;
	}

	public static XWPFParagraph listItem(XWPFDocument document, BigInteger numId, int level, String text) {
		XWPFParagraph paragraph = paragraph(document, text);
		paragraph.setNumID(numId);
		paragraph.setNumILvl(BigInteger.valueOf(level));
		return paragraph;
	}

}

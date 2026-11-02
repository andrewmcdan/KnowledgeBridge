package com.capstone.knowledgebridge.extraction.pdf;

/**
 * One line of text as PDFBox laid it out. fontSize is the size held by most of the line's characters, in points; bold
 * is true when most characters use a bold font; x is where the first character starts, from the left edge of the page,
 * and y is the baseline measured down from the top of the page.
 */
public record PdfLine(String text, float fontSize, boolean bold, float x, float y) {
}

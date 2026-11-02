package com.capstone.knowledgebridge.extraction.pdf;

import java.util.List;

/**
 * One page's text lines in reading order, the page's height in points, and how many images the page draws. number
 * starts at 1.
 */
public record PdfPage(int number, List<PdfLine> lines, float height, int imageCount) {
}

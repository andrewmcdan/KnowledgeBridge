# extraction

Turns uploaded files that are not plain text into Markdown, so they can be ingested exactly like a Markdown upload. `.docx` and text-based `.pdf` are supported.

Status: `.docx` and `.pdf` implemented. Every rejected upload gets a specific reason (see Validation and errors).

## Boundary

- Input: the uploaded file's bytes. Output: a Markdown string, or an `ExtractionException` whose `Reason` says why the file can't be ingested.
- No persistence, no gbrain calls, no HTTP. Saving the item and ingesting it stay in `knowledge` and `ingestion`.
- The converted Markdown becomes the item's `body_text`, and from there the normal Markdown pipeline runs unchanged: `IngestionService` sends it to gbrain, records the attempt, and bumps the knowledge revision (which invalidates the query cache).

```
.docx upload → KnowledgeItemController → DocxMarkdownConverter → Markdown
             → KnowledgeItemService (body_text, source_kind DOCX) → IngestionService → gbrain
```

## Supported formats

| Format | Status | Library | Class |
| --- | --- | --- | --- |
| `.docx` | Implemented | Apache POI (`poi-ooxml`) | `DocxMarkdownConverter` |
| `.pdf` | Implemented | Apache PDFBox | `pdf.PdfMarkdownConverter` |

## Decisions

- **Convert to Markdown, don't chunk.** gbrain chunks and embeds Markdown pages itself, splitting on headings, so keeping Word's heading structure is what gives good chunks. This package never chunks.
- **The original file is not kept.** Retry re-sends `body_text`, so nothing needs the `.docx` after conversion. Only its metadata is stored (see below).
- **Apache POI** is the standard Java library for Office files (the Java counterpart of `python-docx`). Its paragraph styles give direct access to heading levels. Apache Tika was considered and rejected: it pulls in many more dependencies and would still need an XHTML-to-Markdown step.
- **One class per format, no shared interface yet.** The controller picks the converter by file extension. An interface is added when the PDF converter makes a second implementation.
- **Text only.** Formatting that doesn't carry meaning for search (bold, italic) and content outside the main body are left out of the MVP, which keeps the Markdown clean for embeddings.

## `.docx` conversion rules

The document body is read in order, with paragraphs and tables interleaved as they appear in Word.

| Word | Markdown |
| --- | --- |
| `Title` style | `# ` |
| `Heading 1` … `Heading 9` styles | `# ` … `###### ` (levels 7–9 become `######`, Markdown's deepest heading) |
| Normal paragraph | Paragraph |
| Bulleted list item | `- `, indented 4 spaces per list level |
| Numbered list item | `1. `, indented 4 spaces per list level (Markdown renders the numbering) |
| Line break inside a paragraph | Newline |
| Table | Markdown table: first row is the header, `\|` in cell text is escaped, line breaks in a cell become spaces |

- Headings are matched by style **name** (`heading 1`, case-insensitive), not style ID, because Word localizes style IDs (for example `berschrift1` in German).
- Blank paragraphs are dropped. Blocks are separated by one blank line; consecutive list items stay together.
- Merged table cells keep their text in the first cell and leave the rest empty. A table nested inside a cell is flattened into that cell's text.
- Text inside content controls (Word's fill-in fields) is kept.
- Paragraph text is built from its runs rather than POI's `XWPFParagraph.getText()`, because that method appends footnote and endnote text to the paragraph.

Skipped for the MVP:

- Bold, italic, and other run formatting
- Images and other embedded objects
- Comments
- Page headers and footers
- Footnotes and endnotes
- Text boxes and shapes

## `.pdf` conversion rules

**PDF support is best effort.** Nothing in the platform should rely on it. A PDF has no paragraphs, headings, or lists, only text drawn at positions, so structure is inferred from layout, and every PDF producer lays text out differently. The text itself is reliably extracted; structure is not. The rules below are deliberately simple and their gaps are documented rather than tuned per producer, because handling every layout means writing a PDF layout engine, which is outside this project's scope. When the original `.docx` or `.md` exists, upload that instead.

Only the text layer is read. There is no OCR: a scanned or image-only PDF has no text, and a PDF whose text was converted to vector outlines has none either. `PdfContentCheck` rejects those before conversion.

The work is split so that only one class touches PDFBox:

| Class | Role |
| --- | --- |
| `PdfTextExtractor` | Reads each page's lines, split into cells at table gaps, with their font size, boldness, and left and top position (`PdfLine`), plus the page height and image count, including images inside form XObjects (`PdfPage`) |
| `PdfContentCheck` | Rejects a PDF with no selectable text, or with more scanned pages than text pages; pure, no I/O |
| `PdfMarkdownMapper` | Turns those records into Markdown; pure, no I/O |
| `PdfMarkdownConverter` | `convert(InputStream)`, the same shape as `DocxMarkdownConverter` |

| PDF | Markdown |
| --- | --- |
| Text at least 1.2 times the body size, or bold and at least 1 pt larger | `# ` to `### `, largest size first; the third size and anything smaller share `###` |
| Heading text wrapped over several lines | One heading |
| Lines no more than 1.5 font sizes apart | One block; paragraph lines keep their line breaks, which Markdown renders as spaces |
| A larger gap, a new page, or a move back up the page (a new column) | A new block, unless the next line starts in lowercase, which continues the paragraph |
| Line starting with a bullet glyph (`•`, `◦`, `▪`, `●`, `‣`, `⁃`, Symbol U+F0B7, Wingdings U+F0A7) or `- ` / `* ` | `- ` list item |
| Line starting with `1.` or `1)` | Kept as a numbered list item |
| List item further right than the item before it | Nested one level (4 spaces); moving back left returns to that level |
| Line indented past its list item's marker | That item's wrapped text, joined to it |
| Word ending in `-`, `‐`, or a soft hyphen before a lowercase word | Rejoined without the hyphen |
| Two or more consecutive rows of two or more cells that line up (a shared cell position after the first, or the same cell count) | Markdown table; the first row is the header, `\|` in a cell is escaped |
| Other rows of cells | One line per cell in the same paragraph, so neighbouring cells aren't read as one name; a list marker or heading split from its text is joined back |
| Text in the top or bottom 20% of the page repeated at the same height on at least two pages and half of all pages (digits ignored, so page numbers match) | Kept the first time, dropped after |

- Body size is the font size that holds the most characters. Font sizes are rounded to half a point.
- A line is split into cells at every gap between text runs when its text contains real space characters (LibreOffice, Word, and Chrome write them), and otherwise at gaps of 0.95 of the font size or more (TeX writes no spaces; its word gaps stay under 0.85). Cells on one baseline, left to right, form a row. A table row's cells fill the columns in order when it has one per column, and otherwise go to the nearest column of the last row with the most cells, because header cells are often centered.
- Bold comes from the font name (`Bold`, `Black`, `Heavy`, `Demi`, or TeX's `BX` fonts such as `CMBX12`), because embedded fonts rarely set a weight.
- Lines longer than 120 characters, lines ending in a period, and list items are never headings.
- Text is read in content-stream order, which keeps columns apart. Rotated pages are sorted by position instead, because content-stream order splits their text into fragments.

## Validation and errors

Upload goes through the existing `POST /api/knowledge-items/upload` route, which accepts `.md`, `.docx`, or `.pdf`. `.pdf` follows the same checks and limits as `.docx`, with `source_kind` `PDF`. Nothing is saved when any check fails.

| Status | When |
| --- | --- |
| `400` | Empty file (`VALIDATION_ERROR`), or a rejection reason below |
| `413` | Raw `.docx` or `.pdf` over 10 MB, or converted Markdown over 1 MB (gbrain's limit, `GbrainDocument.MAX_BODY_BYTES`) |
| `415` | Filename ends in none of `.md`, `.docx`, `.pdf` |

A rejected file throws `ExtractionException`, and `GlobalExceptionHandler` answers `400` with the reason's code and message in the usual `ApiError` body. The Uploads page shows the message as is.

| Code | When | Message tells the user to |
| --- | --- | --- |
| `FILE_UNREADABLE` | Corrupt file, a different format renamed, a legacy `.doc`, or a password-protected `.docx` | Check the file |
| `FILE_PASSWORD_PROTECTED` | A PDF that needs a password to open | Remove the password |
| `FILE_HAS_NO_TEXT` | `.md` or `.docx` that converts to blank text | — |
| `PDF_NO_SELECTABLE_TEXT` | No page has 20 or more non-whitespace characters: scanned, image-only, or outlined text | Run OCR (Acrobat, OCRmyPDF) |
| `PDF_MOSTLY_SCANNED` | More scanned pages (an image and under 20 characters) than text pages; blank pages are ignored | Run OCR |

A PDF with as many text pages as scanned pages, such as one with a signed scan at the end, is accepted, and the scanned pages contribute nothing.

- **Why 10 MB raw.** A `.docx` is a zip archive and often contains images, so a large file can hold little text. The real limit is the 1 MB on the converted Markdown; the raw limit only stops oversized uploads early. The servlet's own multipart limit (500 MB, set for video) is too loose to rely on.
- POI's default zip bomb protection (`ZipSecureFile`) and its XML parser hardening stay on. Their exceptions are reported as `400`. A document of extremely repetitive text (for example 1 MB of one repeated letter) compresses past the safe ratio and is rejected this way; real documents do not.

## Stored on the knowledge item

| Column | Value |
| --- | --- |
| `source_kind` | `DOCX` (new `KnowledgeSourceKind` value; the column has no CHECK constraint, so no migration) |
| `body_text` | The converted Markdown |
| `original_filename` | The uploaded filename |
| `source_content_type` | The upload's content type |
| `source_sha256` | SHA-256 of the raw file |
| `source_path` | `null`: the file is not kept |

## Dependency

`org.apache.poi:poi-ooxml` (about 15 MB including `poi-ooxml-lite`, `xmlbeans`, and `commons-compress`). POI logs through `log4j-api`, which Spring Boot already routes to its own logging.

`org.apache.pdfbox:pdfbox` 3.0.8 (about 4 MB including `pdfbox-io` and `fontbox`), the standard Java PDF library. It logs through `commons-logging`, which Spring Boot also routes.

## Testing

Unit tests build `.docx` files in memory with POI (`DocxFixtures` in the test tree), so no binary fixtures are needed:

- `DocxMarkdownConverterTests`: headings (including localized style IDs), lists and nesting, tables (pipes, merged cells, short rows, nested tables), content controls, skipped header/footer/footnote/endnote/comment, corrupt and non-Word input
- `KnowledgeItemControllerTests`: `.docx` accepted with its SHA-256, unreadable `400`, no text `400`, converted Markdown over 1 MB `413`, raw file over 10 MB `413`, long filename `400`
- `KnowledgeItemServiceTests` and `KnowledgeBridgeApplicationTests`: the item is saved as `DOCX` with Markdown in `body_text` and no `source_path`

PDF unit tests build PDFs in memory with PDFBox (`PdfFixtures`):

- `PdfTextExtractorTests`: line text, size, boldness, and position; cell splitting with and without space characters; pages without text; image counting, including images inside forms; rotated pages; password-protected and non-PDF input
- `PdfContentCheckTests`: no text, mostly scanned, and allowed scanned and blank pages
- `PdfMarkdownMapperTests`: every rule in the PDF table above, using plain `PdfPage` and `PdfLine` records
- `PdfMarkdownConverterTests`: end to end from PDF bytes to Markdown, and a scanned PDF rejected
- `KnowledgeBridgeApplicationTests`: a scanned PDF upload answers `400` with `PDF_NO_SELECTABLE_TEXT` and its message

For manual and live testing, `synthetic-data/docx/` has three Acme Word documents generated by LibreOffice, with facts to ask about after ingestion.

## Known limitations

- Word text that looks like Markdown (a line starting with `#` or `- `) is not escaped. It only affects how the page renders, not what is searchable.
- Tracked changes are not handled specially; accept or reject them before uploading.
- Headings made by hand (large bold text in a normal paragraph) are treated as normal paragraphs, because only heading styles are recognized.

PDF limitations, found by converting PDFs from LibreOffice, Chrome, pdfLaTeX (one and two columns), enscript, Ghostscript, and a several-page document saved as PDF from Microsoft Word:

- In the Word PDF, tables whose cells wrap over several lines did not survive as tables; each cell's lines became paragraph lines. Heading levels came out inconsistent (sibling headings at `##` and `###`), because Word's heading sizes don't map cleanly onto a size ranking.
- Headings at body size (bold or not) are paragraphs, so a document whose smallest headings match its body text loses them. A bold table header row looks the same.
- A table cell wrapped over several lines breaks the table: the wrapped lines become paragraph lines. A header row of single words packed closer than the font size (TeX, or a Chrome table with narrow columns) stays outside the table as one line.
- Text drawn with tab stops or wide gaps on several aligned lines (a form with labels and values) can become a table.
- Images that are drawn by inline image operators, not XObjects, aren't counted, so such a scanned page counts as blank.
- Chrome draws list bullets as shapes, not text, so lists in Chrome-printed PDFs become plain lines.
- Two paragraphs with no extra space between them merge into one block.
- A one-page document keeps its header and footer text, and a repeated header is kept once, wherever its first page puts it in the text.
- Footnotes appear where they sit on the page, between the surrounding text; superscript footnote marks become stray numbers.
- A compound word broken at its own hyphen (`well-` / `known`) loses the hyphen.
- Lines of text that look like Markdown are not escaped, as with `.docx`.
- A PDF that needs a password to open is rejected. One that opens without a password but forbids copying is still read.

## Frontend note

The Uploads page accepts `.md`, `.docx`, and `.pdf` through the same form and request. It checks the raw size before uploading (1 MiB for `.md`, 10 MiB for `.docx` and `.pdf`); the 1 MiB limit on converted text can only be checked by the backend, which answers `413`.

## Room to grow

PDF handling is intentionally left where it is. Going further would need a scope change and more design, not more rules:

- Tagged PDFs (Word's "Save as PDF" with accessibility tags on) carry real heading, list, and table structure. Reading that structure tree would beat any layout guess for those files.
- OCR would make scanned PDFs usable, at the cost of a native dependency (Tesseract) and slower, less accurate text.
- A dedicated document-layout service or library could take over PDF structure entirely, behind the same converter boundary.
- Smaller gaps: rejecting PDFs whose text extracts as garbage (fonts without a Unicode mapping), and a shared converter interface now that there are two formats.

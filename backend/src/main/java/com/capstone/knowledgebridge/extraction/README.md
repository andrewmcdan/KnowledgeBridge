# extraction

Turns uploaded files that are not plain text into Markdown, so they can be ingested exactly like a Markdown upload. Only `.docx` is in scope now; `.pdf` is planned.

Status: **spec only, not built yet.**

## Boundary

- Input: the uploaded file's bytes. Output: a Markdown string, or an `ExtractionException` when the file can't be read.
- No persistence, no gbrain calls, no HTTP. Saving the item and ingesting it stay in `knowledge` and `ingestion`.
- The converted Markdown becomes the item's `body_text`, and from there the normal Markdown pipeline runs unchanged: `IngestionService` sends it to gbrain, records the attempt, and bumps the knowledge revision (which invalidates the query cache).

```
.docx upload → KnowledgeItemController → DocxMarkdownConverter → Markdown
             → KnowledgeItemService (body_text, source_kind DOCX) → IngestionService → gbrain
```

## Supported formats

| Format | Status | Library | Class |
| --- | --- | --- | --- |
| `.docx` | In progress | Apache POI (`poi-ooxml`) | `DocxMarkdownConverter` |
| `.pdf` | Planned | Apache PDFBox | — |

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
| `Heading 1` … `Heading 6` styles | `# ` … `###### ` |
| Normal paragraph | Paragraph |
| Bulleted list item | `- `, indented 4 spaces per list level |
| Numbered list item | `1. `, indented 4 spaces per list level (Markdown renders the numbering) |
| Line break inside a paragraph | Newline |
| Table | Markdown table: first row is the header, `\|` in cell text is escaped, line breaks in a cell become spaces |

- Headings are matched by style **name** (`heading 1`, case-insensitive), not style ID, because Word localizes style IDs (for example `berschrift1` in German).
- Blank paragraphs are dropped. Blocks are separated by one blank line; consecutive list items stay together.
- Merged table cells keep their text in the first cell and leave the rest empty. A table nested inside a cell is flattened into that cell's text.

Skipped for the MVP:

- Bold, italic, and other run formatting
- Images and other embedded objects
- Comments
- Page headers and footers
- Footnotes and endnotes
- Text boxes and shapes

## Validation and errors

Upload goes through the existing `POST /api/knowledge-items/upload` route, which accepts `.md` or `.docx`. Nothing is saved when any check fails.

| Status | When |
| --- | --- |
| `400` | Empty file; file can't be opened as a `.docx` (corrupt, password-protected, or a legacy `.doc` renamed); conversion produces no text |
| `413` | Raw `.docx` over 10 MB, or converted Markdown over 1 MB (gbrain's limit, `GbrainDocument.MAX_BODY_BYTES`) |
| `415` | Filename ends in neither `.md` nor `.docx` |

- **Why 10 MB raw.** A `.docx` is a zip archive and often contains images, so a large file can hold little text. The real limit is the 1 MB on the converted Markdown; the raw limit only stops oversized uploads early. The servlet's own multipart limit (500 MB, set for video) is too loose to rely on.
- POI's default zip bomb protection (`ZipSecureFile`) and its XML parser hardening stay on. Their exceptions are reported as `400`.

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

## Testing

Tests build `.docx` files in memory with POI, so no binary fixtures are committed. Cases:

- Headings at each level, including `Title`
- Paragraphs and blank paragraph removal
- Bulleted and numbered lists, including nested levels
- Tables, including pipes, line breaks, and empty cells
- Body order with a table between paragraphs
- Skipped content does not appear (header, footer, comment, footnote)
- Corrupt bytes, a non-`.docx` zip, and a document with no text
- Controller: `.docx` accepted, wrong extension `415`, oversized `413`, unreadable `400`, item saved with `source_kind` `DOCX`

## Known limitations

- Word text that looks like Markdown (a line starting with `#` or `- `) is not escaped. It only affects how the page renders, not what is searchable.
- Tracked changes are not handled specially; accept or reject them before uploading.
- Headings made by hand (large bold text in a normal paragraph) are treated as normal paragraphs, because only heading styles are recognized.

## Frontend note

The upload form currently accepts only `.md`. It needs `.docx` added to the file picker's `accept` list; the request is otherwise unchanged.

## Planned

- `.pdf` through Apache PDFBox. PDFs have no heading styles, so structure has to be inferred from font size and layout; expect plainer Markdown than `.docx`.
- A shared converter interface once there are two formats.

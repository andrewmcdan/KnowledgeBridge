# 9. Add documents and manual notes

Adding knowledge requires an Admin account. Open **Uploads** to reach **Add knowledge**. Choose **File upload**, **Folder upload**, or **Manual note**. Content is shared with the workspace after successful ingestion; there is no private-upload option.

## Prepare a supported file

| Format | Requirements and default limits |
| --- | --- |
| Markdown `.md` | Nonempty UTF-8 text; at most 1 MiB |
| Word `.docx` | Readable, unencrypted document; at most 10 MiB |
| PDF `.pdf` | Readable, unencrypted, selectable text; at most 10 MiB |
| Video `.mp4`, `.mov`, `.webm` | Usable audio; server size/duration limits apply; see the video section |

The filename must not exceed 255 characters. Extensions are matched without case differences. Legacy `.doc`, text files with other extensions, spreadsheets, slide decks, archives, and unsupported media are not accepted by the current upload interface. Converting or renaming a file's extension alone does not make its contents valid.

Converted or normalized document text must also fit 1 MiB. A small input file can still fail if its extracted text exceeds the limit. Scanned/image-only PDFs are not OCR-processed. A mostly scanned PDF can be rejected even if some pages contain text; obtain a text-based version or perform approved OCR outside KnowledgeBridge before uploading it.

## Upload one document

1. Select **File upload**.
2. Choose a file with **Drop a file here or browse**, or drop one file on that area. The single-file control handles one item; use Folder upload for a batch.
3. Review **Document title**, which is initially cleaned from the filename. Edit it to make the source recognizable. The source type is detected from the extension.
4. Leave optional AI naming off, or enable it as described below.
5. Select **Add to knowledge** and wait for the result.
6. Inspect the item in **Knowledge list** and confirm its ingestion status.

A saved record is not necessarily indexed successfully. A document can be accepted and saved but return **FAILED** when the engine could not ingest it. Read its error and use the appropriate [retry procedure](12-managing-knowledge.md); do not immediately upload a duplicate.

## Optional AI naming

Select **Use AI to name this file** to generate a title from the document or video transcript. Treat the entered title as guidance, for example `Quarterly planning meeting`, rather than a guaranteed exact result. The final title appears after processing. AI naming is off by default and can incur charges.

The administrator must configure the naming provider. A naming failure leaves a saved failed item with its guidance and content; it does not silently turn off naming or fall back to a successful filename title. Retry ingestion after the cause is corrected. A title already generated successfully is reused if a later indexing step must be retried.

## Create a manual note

1. Select **Manual note**.
2. Enter a concise **Title**.
3. Write the content in **Note**. Use the toolbar for bold, italic, a heading, bulleted or numbered lists, a quote, and a code block.
4. Select **Add note**.
5. Confirm the new item's ingestion result in the list below.

The interface creates notes with the type **Manual note**. A title must be nonblank and at most 255 characters. Note content must be nonblank and fit the normalized 1 MiB UTF-8 limit. Optional AI naming does not apply to manual-note creation.

## Monitor the result

The Knowledge list refreshes every ten seconds while any item is pending or processing. You can also use its refresh button. The list shows character count, source/processing information, and safe failure messages where present. See [Managing knowledge](12-managing-knowledge.md) for the status meanings, editing existing notes, and retrying failed ingestion.

# 10. Upload a folder

Folder upload lets administrators select supported files from a folder and its subfolders. Files are sent one at a time through the same ingestion workflow as single uploads. This is a browser-held batch, not a persistent background synchronization service.

## Select and review a batch

1. Open **Uploads** and select **Folder upload**.
2. Enter **Document type for these files**. The initial value is `Document`; the value applies to every file in this batch.
3. Optionally enable **Use AI to name these files** and enter shared title guidance. Each file is named from its own content or transcript. Leave guidance blank to use each filename as context.
4. Select **Choose a folder** and confirm the browser's folder-selection prompt.
5. Review the eligible-file and skipped-file counts before selecting **Upload folder**.

Markdown, DOCX, PDF, MP4, MOV, and WebM files are eligible. Other extensions are skipped. Supported files still undergo size, content, and server validation. Nested relative paths distinguish same-named files in different subfolders during this batch; these paths are not a new folder hierarchy in the Knowledge Base.

The preview shows the first 100 entries when a folder contains more than 100 eligible files. All eligible files are still processed. Read the total count rather than assuming only the previewed entries will be sent.

## Understand batch statuses

| Batch status | Meaning |
| --- | --- |
| ready | Selected in the browser and not yet sent |
| uploading | The current file request is in progress |
| queued | The server accepted the request and returned a knowledge item |
| failed | Local validation or the upload request failed |

**queued** is not the same as **COMPLETED** ingestion. The server can accept a request whose returned knowledge item has failed processing. Follow the separate item status in the Knowledge list below the batch. A video can remain queued or processing long after the browser has finished uploading it.

## Stop or resume carefully

Select **Stop after current file** to stop before the next file. The active request is allowed to finish. Remaining entries have not been sent. When the batch is idle, use **Upload remaining / retry failed** to send entries that were not already marked queued.

Correct a local problem, such as an unsupported size, before retrying it. The retry action does not resend entries already accepted by the server. To retry ingestion of an accepted-but-failed knowledge item, use **Retry** in the Knowledge list instead.

Keep the tab open until uploads finish. Navigating away, closing the tab, or selecting a folder again discards the batch's browser-held progress. Selecting the folder again starts a new batch and can create duplicates; there is no cross-batch deduplication or resume checkpoint. Check the existing Knowledge list before restarting a lost batch.

## Browser support and preparation

Folder selection relies on the browser's directory-picker support. If **Choose a folder** cannot select a directory, use supported individual file uploads or another approved browser. An archive is not a substitute: ZIP extraction is not implemented.

Remove unrelated files before selecting a large directory. Review the shared document type and naming choice once for the whole batch. Transcription and AI naming can incur charges separately for each eligible file. This workflow uploads selected content; it does not watch the folder for later edits or deletions.

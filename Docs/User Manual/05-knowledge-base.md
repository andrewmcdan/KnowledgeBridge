# 5. Knowledge Base and document previews

## Browse available knowledge

Open **Knowledge Base** to see documents, newest created first. Standard users see only active, completed items. Administrators can see all ingestion statuses and choose **Active**, **Inactive**, **Trash**, or **All states** in **Document state**. The default admin view is Active, so an admin can see an active item that is still processing or has failed.

Use **Refresh list** to request current information. The count reflects the loaded view and its filters, not a separate global inventory. This version has no server-side page navigation or source-kind filter. Jira items appear alongside other knowledge.

## Filter by title or type

Type in the top **Search knowledge base** field and press Enter. Filtering matches the loaded items' titles and document types without case differences. Clear the field to remove that filter. For administrators, title/type filtering combines with the lifecycle filter.

If a document is absent, check the spelling, clear the search, refresh the list, and verify its state and ingestion result with an administrator. A successful upload acknowledgement does not necessarily mean indexing completed.

## Read a document

1. Select the document title or its available preview control.
2. Read the complete displayed text, including headings, lists, tables, and code where present.
3. Expand **Metadata** to inspect the application document ID, type, and any parsed document front matter.
4. Expand **Detected entities** to inspect the associated detections, their status, and source links.
5. Use **Close document** or Escape when finished.

Markdown front matter is shown as metadata rather than mixed into the main body. Malformed metadata can be retained with an explanatory message while the document body stays readable. A PDF or Word upload is shown as extracted Markdown/text, not as an exact reproduction of its original page layout. A video source is represented by its transcript; the preview is not a video player or timestamp-seeking interface.

The preview does not execute embedded HTML. Remote images may be represented as links instead of loaded images. Follow external links only when appropriate for your workspace. The document viewer is not a download/export-original-file tool.

## Passages opened from a query

Opening a search hit or synthesis citation adds the submitted question and related text to the preview's passage-finding context. When matching passages are available, use the previous/next controls to move among them. The current match is emphasized; navigation can wrap between the first and last match.

Matching is an aid to reading, not a verification score. A changed document, paraphrase, short snippet, or unmatched citation may produce no highlight. The full retained document remains readable. Check nearby headings, dates, and the surrounding paragraph rather than treating a highlighted fragment as the entire source.

## Access and management boundaries

Administrators retain preview access to completed inactive or trashed documents for review and restoration. Standard users cannot read excluded document content or its entity metadata. Documents that have not completed ingestion may not have readable content through the ordinary preview endpoint.

Admins use the lifecycle controls on Knowledge Base to activate, deactivate, trash, restore, or permanently remove items. Manual-note editing and ingestion retry are on **Uploads**. See [Managing knowledge](12-managing-knowledge.md) before changing a source that others rely on.

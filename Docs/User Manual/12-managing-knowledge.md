# 12. Manage ingestion and document lifecycle

Administrators manage ingestion in **Uploads** and availability in **Knowledge Base**. Ingestion status and lifecycle state are separate: a completed document can be inactive or in Trash, while an active document can still be processing or failed.

## Read ingestion status

| Status | Meaning | Next step |
| --- | --- | --- |
| PENDING | Saved or queued, with processing still to start | Wait and refresh; do not create a duplicate |
| PROCESSING | An ingestion stage is running | Follow its stage; management actions are temporarily unavailable |
| COMPLETED | Content was stored in the knowledge engine | Check ACTIVE state; allow indexing/extraction to catch up |
| FAILED | The saved item could not finish ingestion | Read its error, correct the cause, and retry the existing item |

**COMPLETED** does not certify document accuracy, entity-extraction success, or immediate semantic-search readiness. **FAILED** does not necessarily mean no application record was created.

## Retry ingestion

1. Open **Uploads** and find the failed item in **Knowledge list**.
2. Read its error and correct the underlying cause with the deployment administrator if necessary.
3. Ensure the item is Active; excluded items cannot be retried through a stale request.
4. Select **Retry** once and inspect the returned status.

Document/note retries can finish within the request. Video retries can return to a queued background state. Processing history is retained by the application, but the UI does not provide a full attempt-history browser. A pending or processing item cannot be retried or lifecycle-managed until it finishes.

## Edit a manual note

1. In **Uploads**, locate a completed or failed manual note that is Active.
2. Select **Edit note**.
3. Update **Note title**, **Note type**, or **Note content**.
4. Select **Save and reindex**. Review the resulting ingestion status.

The editor is for manual notes, not uploaded Markdown, converted documents, video transcripts, or Jira-sourced content. An edit can be saved even if reindexing subsequently fails; check the notice and retry the existing note if needed.

Another edit or state change can make your loaded revision stale. If saving returns a conflict, copy unsaved text somewhere safe before choosing **Reload saved note**, because reloading replaces the editor contents. Compare with the latest note and apply only the intended changes. There is no general revision-history, merge, or undo screen. **Cancel editing** leaves the saved note unchanged and discards that editing session.

## Change availability on Knowledge Base

Choose the appropriate **Document state** filter, locate the item, select an action, and review its confirmation dialog.

| Action | Result |
| --- | --- |
| Deactivate | Retain content and metadata but exclude the source from ordinary retrieval and its graph relationships |
| Activate | Make an inactive item Active; completed retained content is reindexed, while failed content still needs ingestion retry |
| Move to Trash | Exclude the source and retain its content for restoration; remember whether it was previously Active or Inactive |
| Restore | Return a trashed item to its previous Active/Inactive state |
| Permanently delete | Remove the application item and associated retained data; only offered in Trash |

Refresh the list after a state change or uncertain result. If the engine cannot complete the requested exclusion or reindexing, the application reports failure instead of claiming the transition succeeded. Do not repeatedly replay a destructive action without first checking the current state.

## Permanent deletion and retention

Permanent deletion requires typing **DELETE** in the confirmation dialog. It removes application content, associated attempts/entity memberships, orphaned associated entities, and any retained source file, and clears cached answers that could contain removed text. It cannot be undone using Restore.

The engine page is excluded immediately; physical engine cleanup follows the configured engine retention process. Backups can retain older copies. This action is not a promise of immediate physical erasure from every store. Ask the deployment operator to reconcile retention and backups when deletion requirements go beyond application visibility.

Successful knowledge changes invalidate older cached answers. Existing displayed answers are browser state; rerun a query to obtain a response checked against current availability. Standard users cannot use ordinary document or entity reads to access excluded sources.

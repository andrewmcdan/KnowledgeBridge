# 13. Sync Jira work items

Jira sync copies project work items into KnowledgeBridge so their descriptions and comments can participate in ordinary knowledge search and synthesis. It is a read-only import from Jira, not a live Jira editing interface.

## Before you sync

An administrator must configure and verify the Jira connection on the backend. The sync panel is on **Knowledge Base** and is visible to administrators only. If the connection is unconfigured or unreachable, the project selector may not appear. A separate Jira View page is not implemented.

The integration uses the configured Jira account's access, not the KnowledgeBridge user's individual Jira permissions. Every signed-in KnowledgeBridge user can read active, completed synced content. Import only a project approved for that shared audience.

## Run a project sync

1. Open **Knowledge Base** with an Admin account.
2. Find **Jira project** in the Jira sync toolbar.
3. Choose the project to import.
4. Select **Sync Jira**.
5. Watch the run status and counts. The panel checks a running sync periodically and refreshes the knowledge list when the run finishes.
6. Review any failures, then confirm the imported items are active and completed before relying on them in Ask AI.

Only one active run for a project is allowed at a time. The **Syncing** state disables duplicate requests in this panel. A conflict can mean the project is already being synced elsewhere. Do not assume a large project's run finished just because its first items appeared.

## Interpret counts and failures

**Created** means a new Jira-sourced knowledge item was added. **Updated** means changed content was re-ingested. **Unchanged** includes items that did not require a new ingestion; deactivated or trashed items are deliberately left excluded. **Failed** means an item did not finish successfully. Read both the run state and its counts: a finished run is not proof that every item succeeded.

For a failed or interrupted run, preserve the displayed error, check the service connection with the operator, and sync the same project again after the cause is corrected. A later run can revisit failed or interrupted work even if the Jira work item was not edited again. Re-ingestion temporarily makes lifecycle actions unavailable, preventing a trash/deactivate request from being overwritten during processing.

Some failures affect one work item and others affect the entire connection. If counts do not progress, avoid repeated simultaneous starts and ask the operator to inspect safe server diagnostics. Restarted applications mark interrupted active runs as failed so the project can be synced again.

## Use synced content

Synced titles usually include the Jira key and summary. Items appear with other Knowledge Base documents. Open their stored page through an Ask AI citation just as you would another source. The page can include Jira status, people, parent information, description, comments, links, and a browse URL.

Answers reflect the **last successful sync**, not current Jira state. The application does not provide a special citation link that opens the Jira website in place of its stored document preview. Use a source's Jira browse URL when you need to inspect the original system, subject to its own authentication and access controls.

## Important limitations

- Edit Jira work items in Jira; KnowledgeBridge does not write changes back.
- Sync is started manually, not automatically by a webhook or schedule in this interface.
- Deleting an issue in Jira is not automatically detected as a deletion in KnowledgeBridge. Admins must manage the stored item's lifecycle.
- Permanently deleting a Jira-sourced item in KnowledgeBridge does not delete it in Jira; a later sync can import it again. Use deactivation or Trash when the intent is to keep an item excluded from normal syncing.
- Link-only Jira changes may not update its modification timestamp, so a stored page can retain older links until another content update is detected.
- There is no Knowledge Base source filter or dedicated Jira-only navigation view in this version.

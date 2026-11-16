# 16. Troubleshooting and support

Start by reading the message beside the affected action. Identify whether the problem is sign-in, an upload request, a saved item's ingestion, a query, or an optional integration. A successful acknowledgement at one stage is not proof that every later stage finished.

## Sign-in and navigation

| Symptom | What to check |
| --- | --- |
| Email/password rejected | Recheck the intended account and exact password; a disabled account has the same outward login failure. Contact the administrator if necessary. |
| Email already registered | Use Sign in with that email; do not create duplicates. |
| Session expired | Sign in again. Remember Me does not extend token expiry. |
| Uploads or Admin missing | Your session is probably a User session. After promotion, sign out and sign in again. |
| Forgot password does nothing | Password reset is not implemented; contact the operator. |
| API & database connected but AI fails | The health indicator does not verify providers, OAuth, indexing, transcription, or Jira. |

## Queries and documents

| Symptom | What to do |
| --- | --- |
| No Search results | Clear overly narrow wording, check the selected mode, and confirm the expected source is active and completed. Try distinctive source terms. |
| Fewer hits than the limit | The limit is a maximum. Matches or available documents can be fewer. |
| Semantic retrieval warning | Retry after the operator checks indexing/service health; keyword evidence may still be available. |
| Synthesis gives gaps or an extractive fallback | Read the sources and warnings. Add missing evidence through an admin or ask a narrower question. |
| Referenced document no longer available | Refresh and rerun the question; a source may have been changed or excluded. |
| No matching highlighted passage | Read the full current source. The answer may be paraphrased or based on an earlier version. |
| Top search misses body text | The top field filters titles/types. Use Ask AI Search for indexed passages. |
| Preview loads but entities fail | Retry loading entities separately. Document readability and entity extraction have separate states. |

Avoid sending the same synthesis repeatedly during an outage. Cancel stops waiting in the browser, not necessarily the paid server call. Preserve the exact safe message and contact the operator when the same service failure persists.

## Uploads and processing

| Symptom or code | What to do |
| --- | --- |
| File too large / content exceeds limit | Check both input-file size and extracted-text limits. Split or simplify the source outside the application. |
| File unreadable or password protected | Supply a valid, unencrypted supported document. Renaming an extension does not convert its contents. |
| No selectable PDF text / mostly scanned PDF | Obtain a text-based PDF or perform approved OCR externally. |
| Saved, but ingestion FAILED | Keep the existing item, read its error, fix the cause, and use Retry. |
| AI_TITLE_FAILED | Ask the operator to check naming credentials/service; retry the existing item when corrected. |
| VIDEO_HAS_NO_AUDIO / VIDEO_TOO_LONG | Use an audio-bearing recording within configured duration limits. |
| VIDEO_SOURCE_MISSING | The retained source needed for retry is unavailable. Ask the operator to reconcile the item; a new approved upload may be necessary. |
| TRANSCRIPT_TOO_LARGE | The resulting text exceeded the knowledge-body limit. Use a shorter source or a separate prepared text version. |
| Item stays pending or processing | Refresh the Uploads list, note the stage, and ask the operator to check the background worker. Do not delete/reupload blindly. |
| Folder batch says queued, but item failed | Queued means server-accepted. Retry ingestion from the Knowledge list, not the accepted batch entry. |

Keep a copy of a draft note before reloading after a conflict. Wait for pending/processing work before lifecycle changes. An uncertain lifecycle request should be followed by a list refresh before another attempt.

## Entities, Dashboard, and Jira

Use **List view** if the 3D map is unavailable or difficult to navigate. Clear graph filters and consider its bounded entity/document limits before concluding that a relationship is absent. Use **Refresh graph** after content changes. Verify entity names and summaries against source text; the current UI has no entity-correction/verification editor.

Check **Demo mode** before interpreting analytics. Personal and workspace usage have different scopes, and query estimates exclude other processing costs. A blank cost chart can mean cost data is unavailable, not that provider charges are zero.

If the Jira toolbar is absent, ask the operator to verify configuration and reachability. For a failed run, record the project, run state, counts, and safe message, then retry only after the cause is corrected. Remember that Jira deletions do not automatically remove already imported knowledge.

## Report a useful problem

Send the administrator or project maintainer:

- The affected screen and exact steps, including the selected mode or action.
- A safe timestamp and your role, not your password or token.
- The document ID/title or Jira project/run identifier when appropriate to share.
- The displayed error code/message and processing stage, if available.
- Whether the problem affects one item or all items, and whether a refresh or new sign-in changed it.

Redact secrets and unrelated document content from screenshots and logs. Do not attach entire private recordings or credential files merely to demonstrate a failure. Give the operator enough evidence to reproduce the affected operation with approved sample content.

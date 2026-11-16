# 8. Read usage and cost analytics

## Open real workspace analytics

Open **Dashboard** and locate **AI Analytics Dashboard**. Leave **Demo mode** off when reviewing actual activity. Select **Refresh analytics** after queries or content changes if you need a new snapshot.

With Demo mode on, the analytics panels show fixed presentation data, clearly labeled as such. This does not create real usage, change knowledge, or configure the engine. It also does not turn the separate knowledge map into a demo graph. Turn Demo mode off before reporting statistics.

## Interpret the main metrics

| Metric | What it means |
| --- | --- |
| Successful queries | Recorded successful Search and Synthesis requests since tracking began, including cache hits |
| Total tokens used | Recorded input plus output tokens for queries |
| Cache hit rate | Recorded cached responses divided by recorded successful queries |
| Input vs output tokens | Daily recorded token usage in the last 30 UTC calendar days |
| Usage by Model | Available model-level query/token information for that reporting window |
| Cached Response Hits | Recorded cache-hit total and its trend |
| Token Spend & Cost Estimate | Available estimated query-provider cost, not an invoice |

Standard users receive their personal query usage. Administrators receive workspace-wide query usage. Check **Your total** versus **Workspace total** before comparing results. Knowledge and entity panels describe accessible workspace content, so they are not necessarily personal inventories even when your query metrics are personal.

Search and cache hits report zero new synthesis tokens. When the provider does not return token counts, KnowledgeBridge can estimate them from text. A generated answer does not guarantee an exact provider token count, and an unknown cost should not be treated as zero billed cost.

The lifetime cards and 30-day charts have different time scopes. Daily trends use UTC date boundaries and can differ from your local calendar day. The interface currently has no arbitrary date-range selector or analytics-export button.

## Know what the numbers exclude

These are query-usage records. They do not provide a complete bill for video transcription, optional AI upload naming, entity extraction, embeddings, hosting, storage, or other provider operations. Provider cost information may be unavailable for older or unsupported responses. Usage recording is best-effort, so a completed query can be missing from totals if its accounting write failed.

Cache hits avoid another synthesis call. A first-time synthesis, a changed question, or a changed knowledge revision may require a new call. Canceling in the browser does not guarantee that an already started provider call stopped charging.

> Note: Use the provider's own billing system for financial reconciliation. Dashboard estimates help understand activity but do not replace invoices or provider limits.

## Knowledge insight panels

**Document Types** groups completed items by their original source kind, such as Markdown, DOCX, PDF, manual note, video, or Jira. The current video row is labeled MP4 even though MOV and WebM are accepted too. The admin's loaded items can include completed inactive or trashed content; the percentages are not an active-only retrieval count.

**Upload / Ingestion Status** shows recent completed items, not the full processing queue. Administrators should use **Uploads** for pending, processing, or failed jobs. **Known Entities** is based on a bounded entity list; a displayed cap is not an exact workspace-wide total. **No current data** means no reportable data is available for that panel or period; it does not establish that the entire system is empty.

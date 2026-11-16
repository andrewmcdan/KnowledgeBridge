# 6. Browse detected entities

Entities are names detected in indexed knowledge: people, companies, projects, and teams. Each entity can have a summary and links to source documents. Detections are machine-produced references, not an authoritative directory or proof of a relationship.

## Find an entity

1. Open **Entities**.
2. Enter part of a name in **Filter by name**.
3. Optionally choose **People**, **Companies**, **Projects**, or **Teams** in **Type**.
4. Choose how many results to show: 25, 50, or 100.
5. Select an entity to open its details and related documents. Select a source document to read the original context.

Filtering updates the current result set. The entity screen checks for updates about every ten seconds while visible and refreshes when the tab regains focus. A bounded result list is not a complete count of every entity in the workspace. Adjust filters or increase the limit if the entity you need is missing.

Use **Back to all entities** to leave a selected entity and return to the browser. An entity link opened from a document or graph can select that entity directly.

## Interpret an entity correctly

Check its type, summary, source-document count, and verification label. **Unverified** means you should inspect the sources. A **Verified** label, if returned by the service, is metadata about that record; this version has no user-facing verification or correction action. Do not assume that reading or opening a record marks it verified.

An extraction can mistake status values, headings, or ordinary phrases for names. Similar names can represent different records, while repeated names can refer to the same person. Open the associated documents to resolve the meaning. Counts are scoped to the records and document lists available through the application.

## Inspect entities within a document

Open a document preview and expand **Detected entities**. Entries are grouped by type, with names, summaries where available, and verification/source counts. Select a name to open its entity page.

| Message or state | Meaning and next action |
| --- | --- |
| Not extracted yet | The document has no completed extraction result. An admin can extract it. |
| No entities were detected | Extraction completed but returned no entities; read the source normally. |
| Extraction could not complete | The document can remain readable even though extraction failed. Ask an admin to retry. |
| Detection limit reached | Only part of the extraction is displayed or stored; absence from the list is not proof of absence from the text. |
| Entity lookup error | Use **Retry loading entities**; this reloads existing metadata rather than requesting another extraction. |

The document disclosure shows up to 100 entities. An entity extraction failure does not, by itself, mean the document failed ingestion.

## Extract or retry entities as an administrator

1. Confirm the source is an active, completed document.
2. Open its preview and expand **Detected entities**.
3. Select **Extract entities** when no extraction exists, or **Retry entity extraction** when offered.
4. Wait for the result and review its status separately from the document's ingestion status.

Extraction can call an AI provider and incur charges. Do not repeatedly retry without reading the error. Extraction controls are not available to standard users, and excluded sources must not be reintroduced through this workflow. The graph refreshes in response to successful extraction updates; use **Refresh graph** if its snapshot is older.

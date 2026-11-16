# 17. Quick reference and glossary

## Default limits at a glance

Deployment-configurable values can differ. The checked-out application's validation messages and operator configuration take precedence over defaults.

| Item | Default or implemented limit |
| --- | --- |
| Search question | 1-1,000 normalized UTF-16 code units |
| Synthesis question | 1-2,000 normalized UTF-16 code units |
| Search result limit | Integer 1-50; default 10 |
| Name, email, document title/type | Nonblank where required; maximum 255 characters |
| Registration password | At least 8 Unicode characters; maximum 72 UTF-8 bytes |
| Markdown input / normalized knowledge text | 1 MiB |
| DOCX or PDF input | 10 MiB |
| Video upload / duration | 500 MB / two hours; configurable |
| Default sign-in token lifetime | Eight hours; configurable |
| Entities list size | 25, 50, or 100 |
| Graph snapshot | 25, 50, or 100 entities; up to 100 documents per entity |
| Document entity disclosure | Up to 100 entities |
| Folder batch preview | First 100 entries shown; all eligible files processed |

MiB is a binary unit: 1 MiB equals 1,048,576 bytes. Byte-based limits differ from a text character count. Unicode characters may use several UTF-8 bytes or two UTF-16 code units.

## Everyday checklist

1. Sign in with the correct account and role.
2. Use Knowledge Base for title/type browsing and Ask AI for passage search or synthesis.
3. Check the result's source, warnings, and current document context.
4. Distinguish actual analytics from Demo mode.
5. Sign out when finished, especially on a shared device.

## Administrator content checklist

1. Confirm content is approved for the whole workspace.
2. Choose a supported source and meaningful title/type.
3. Decide whether optional paid transcription/naming/extraction is appropriate.
4. Inspect the saved item's ingestion result rather than only the upload acknowledgement.
5. Confirm the source is active and completed; verify retrieval after indexing catches up.
6. Correct manual notes in place where appropriate; manage replaced sources explicitly.
7. Use deactivate/Trash for reversible exclusion and understand retention before permanent deletion.

## Glossary

| Term | Meaning in KnowledgeBridge |
| --- | --- |
| Active | The lifecycle state eligible for retrieval when ingestion is also completed |
| Citation | A reference from an answer to a stored knowledge source |
| Entity | A detected person, company, project, or team reference |
| Exact-match cache | A stored generated answer reusable for the same normalized question and knowledge revision |
| Extractive fallback | Retrieved source text returned instead of a complete generated synthesis |
| Ingestion | The process of preparing and storing content for knowledge retrieval |
| Knowledge gap | A reported absence of sufficient source evidence for part of an answer |
| Knowledge revision | A version marker used to invalidate answers after knowledge changes |
| Lifecycle | Active, Inactive, or Trash availability state, independent of ingestion status |
| Projection/index | Engine data used for retrieval, which can catch up after a document is stored |
| Query usage | Recorded activity from successful Search/Synthesis requests, not every provider operation |
| Shared-document connection | A graph link supported by co-occurrence in one or more documents |
| Synthesis | An AI-generated answer produced from retrieved workspace knowledge |
| Transcript | Text extracted from prerecorded video audio |

## Maintained project references

These sources help administrators and maintainers confirm deployment-specific details. They are technical references, not additional user-interface screens.

- [Project README](../../README.md): startup modes, local addresses, and verification commands.
- [Integration and authentication contract](../Integration-and-authentication.md): access, registration, requests, errors, naming, and usage semantics.
- [Knowledge lifecycle](../Knowledge-lifecycle.md): exclusion, restoration, deletion, and retention boundaries.
- [Jira integration design](../Jira-Integration-Design.md): connection setup, scopes, sync behavior, and known limitations. Read current implementation/configuration if an older design paragraph conflicts with it.
- [OpenAPI contract](../openapi.json): implemented request and response models.
- [Launcher and maintenance scripts](../../scripts/README.md): host development and guarded operational procedures.
- [Docker configuration](../../docker/README.md): engine/database initialization and persistent storage.

This manual was checked against the repository's implemented routes, components, validation, and configuration. It documents supported behavior; it does not claim a newly performed live provider or browser acceptance test. Recheck the relevant source and update the manual when changing a workflow, label, permission, or limit.

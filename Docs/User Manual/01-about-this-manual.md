# 1. About this manual

KnowledgeBridge helps a workspace find and use the knowledge stored in its documents, notes, prerecorded videos, and synced Jira work items. You can search for relevant passages, ask for a cited AI answer, explore detected entities, and follow connections back to their source documents.

This manual describes the implemented web application in this repository. Your administrator supplies the address and configures the services behind it. Available documents, AI models, costs, and processing limits depend on that deployment. Examples in this manual use fictional information; they do not promise that the example documents exist in your workspace.

## Choose your starting point

| Your task | Start here |
| --- | --- |
| Sign in or create an account | [Getting started](02-getting-started.md) |
| Find an answer with supporting evidence | [Ask AI](04-ask-ai.md) |
| Read a complete document | [Knowledge Base and document previews](05-knowledge-base.md) |
| Explore people, projects, and teams | [Entities](06-entities.md) and [Knowledge map](07-knowledge-map.md) |
| Add, correct, or remove knowledge | [Adding knowledge](09-adding-knowledge.md) and [Managing knowledge](12-managing-knowledge.md) |
| Connect or operate the application | [Administrator setup and operations](15-administrator-operations.md) |
| Resolve a problem | [Troubleshooting](16-troubleshooting.md) |

## Roles and shared knowledge

**User** accounts can read active, completed knowledge, use Ask AI, browse entities, and use the Dashboard. **Admin** accounts can also add and manage content, sync Jira, and grant admin access. The Uploads and Admin navigation entries appear only for administrators. The backend enforces these permissions as well as the interface.

KnowledgeBridge currently has one shared knowledge corpus. A document's contributor or owner is not a private-access setting. Newly registered users can read the workspace's available knowledge immediately; there is no per-document private/share control or invitation-approval step. Add only material approved for everyone who can join this deployment.

## How knowledge becomes available

An administrator adds content. KnowledgeBridge extracts or transcribes text, optionally generates a title, and sends the content to its knowledge engine for indexing. A document becomes eligible for ordinary reading and queries when its ingestion status is **COMPLETED** and its lifecycle state is **ACTIVE**. Semantic indexing and entity extraction can finish separately, so a newly completed document may need more time before every feature reflects it.

Search snippets, AI answers, and entity detections are ways to locate evidence. Read the underlying document before relying on an important claim. A graph link means two entities occur in a shared document; it does not establish a verified business relationship.

> Important: AI-generated answers and detected entities can contain mistakes. Missing evidence, conflicting source material, and older synced content can also affect an answer. KnowledgeBridge is a research aid, not an approval or verification system.

## Conventions and scope

Bold text identifies visible labels such as **Knowledge Base** and **Refresh list**. Numbered steps describe an action sequence. Code text identifies a filename, address, status, or command. Notes distinguish limitations from procedures.

The manual covers current browser workflows and the local deployment procedures maintained with the project. It does not describe planned features as available. In particular, there is no implemented password-reset flow, persistent chat-history screen, bookmark workflow, per-document sharing control, or standalone Jira View page. See the [reference](17-reference.md) for limits and source documentation.

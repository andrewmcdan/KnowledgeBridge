# 3. Workspace navigation

## Find the right screen

| Navigation entry | What you use it for | Who can use it |
| --- | --- | --- |
| Dashboard | Usage analytics and the knowledge relationship map | User and Admin |
| Ask AI | Ranked passage search and cited synthesis | User and Admin |
| Knowledge Base | Browse and read available documents; admins manage lifecycle and Jira sync | User and Admin |
| Entities | Find detected people, companies, projects, and teams | User and Admin |
| Uploads | Add files, folders, and manual notes; inspect ingestion and retry failures | Admin |
| Admin | Find accounts and grant admin access | Admin |

Select an entry in the left sidebar. The KnowledgeBridge brand link opens Ask AI. **Collapse** makes the sidebar smaller; **Expand sidebar** restores it. On a narrow screen, use **Toggle navigation** to open or close the menu. Selecting a destination closes the mobile menu.

## Use the top search field

**Search knowledge base** filters the Knowledge Base by document title or document type. Type a term and press Enter to open that view. It is not a full-text or semantic query field. To search document passages or ask a question, open [Ask AI](04-ask-ai.md).

Press **Ctrl+K** on Windows/Linux or **Command+K** on macOS to focus the top search field. Press Tab and Shift+Tab to move between controls and Enter or Space to activate ordinary buttons. The 3D map has a keyboard-friendly **List view** alternative.

## Understand the connection indicator

The sidebar's connection indicator reports application API and database health. **API & database connected** does not verify AI-provider credentials, semantic indexing readiness, video transcription, entity extraction, or Jira connectivity. Those services can fail independently while the core application remains connected.

If a page reports an error, use that page's refresh or retry control after checking the cause. Reloading the entire browser may discard a question, selected folder batch, or unsaved note. Do not repeatedly resubmit a paid operation merely because it is taking time.

## Open and close details

Document titles, **View document**, source citations, and graph source-document controls can open a document preview. The preview is a dialog with its own scrollable content. Use **Close document** or Escape to return to the previous screen. Clicking the backdrop also closes the document dialog.

An open document's metadata and detected entities start in collapsible disclosures. Expand the relevant disclosure to inspect them. A preview can show text successfully even when the separate entity lookup fails; follow the message in the entity area rather than assuming the document itself failed.

## Know what is saved

Submitted content is saved only when the server accepts the relevant create or save action. Folder selection, graph presentation settings, the current question, and displayed answers are browser-page state. This version has no persistent conversation list, draft-recovery screen, or saved-answer/bookmark workflow. Keep a separate copy of important draft text before navigating away.

The sidebar's **Workspace summary** is currently a placeholder. Use the Dashboard for implemented metrics, and check whether its **Demo mode** is enabled before interpreting numbers.

On wider layouts, the supporting column provides a small detected-entity list and recent ingestion information. **View all entities** and **View all documents** open the corresponding screens. Its **Related Documents** area is currently a placeholder, not a list tailored to your question. Use actual Search results or answer citations for source evidence.

# 7. Explore the Dashboard knowledge map

The Dashboard contains an analytics area and a **Knowledge map**. The map connects detected entities through the documents they share. It is an exploration view of the available source material, not a verified organizational chart.

## Read the graph

An entity is a node. A connection is a link between two entities that appear in at least one shared document. There is one link per entity pair; several documents can support the same link. Colors identify the entity types shown in the legend.

The visible **Entities**, **Connections**, and **Source documents** totals describe the current filtered graph. They are not a complete inventory of the database. Node size can reflect the number of source documents when **Size nodes by document count** is enabled.

## Find relevant connections

1. Open **Dashboard** and locate **Knowledge map**.
2. Enter a name in **Filter entities**. Matching entities and their immediate connections can remain visible to preserve context.
3. Choose an **Entity type** if you want a narrower view.
4. Use **Shared documents** to require one, two, three, or five supporting documents for a connection.
5. Select an entity or a link. Review the details panel and open its source documents.
6. Use **Clear selection** to reset the inspector, or **Clear filters** to return to the broader snapshot.

Selecting a node shows its summary, connected entities, and source documents. Selecting a link shows the documents shared by the two entities. An isolated node can legitimately remain visible when no connection meets the filters.

## Move around the 3D view

| Control | Effect |
| --- | --- |
| Drag the background | Rotate the view |
| Scroll or pinch | Zoom |
| Right-drag | Pan |
| Drag a node | Reposition it in the layout |
| Select a link | Open the shared-document evidence |
| Fit graph | Reframe the visible graph |
| Pause layout / Resume layout | Stop or resume the layout simulation |
| Hide labels / Show labels | Change label visibility |

Expand **View settings** to adjust layout spacing, node size, link thickness, link opacity, and label size. You can also toggle labels and document-count sizing or choose **Reset view settings**. Changing spacing resumes and refits the layout. These are presentation settings; they do not change stored knowledge or verify entities.

## Use List view

Select **List view** for keyboard-accessible entity and connection selection, or when 3D graphics are unavailable. The same evidence-oriented details panel is used. If the 3D graphics connection is lost, use List view or reload the page after preserving unsaved work elsewhere.

## Understand snapshot limits

**Entity limit** offers 25, 50, or 100 entities; the graph initially requests 50. The map also loads at most 100 related documents per entity. A warning appears when a limit may have been reached. Connections outside the loaded snapshot are not shown, and missing connections are not evidence that two entities never occur together.

Use **Refresh graph** after knowledge changes. The graph also responds to entity-extraction updates, but it is not an unrestricted live query over every record. No entities yet usually means the workspace needs indexed content and a completed entity extraction, not that the graph needs manual node creation.

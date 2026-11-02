import { useState } from "react";
import { ENTITY_COLORS, type EntityGraph, type GraphSelection } from "./graphData";

export function GraphList({ graph, selection, onSelect }: { graph: EntityGraph; selection: GraphSelection; onSelect: (selection: GraphSelection) => void }) {
    const [shown, setShown] = useState(50);
    const names = new Map(graph.nodes.map((node) => [node.id, node.name]));
    return (
        <div className="graph-list-view">
            <h3>Entities ({graph.nodes.length})</h3>
            <ul className="graph-entity-list">
                {graph.nodes.map((node) => (
                    <li key={node.id}>
                        <button type="button" aria-pressed={selection?.kind === "entity" && selection.id === node.id} onClick={() => onSelect({ kind: "entity", id: node.id })}>
                            <i style={{ background: ENTITY_COLORS[node.type] }} />
                            <span>{node.name}</span>
                            <small>{node.documents.length} docs</small>
                        </button>
                    </li>
                ))}
            </ul>
            <h3>Shared-document connections ({graph.links.length})</h3>
            <ul className="graph-connection-list">
                {graph.links.slice(0, shown).map((link) => (
                    <li key={link.id}>
                        <button type="button" aria-pressed={selection?.kind === "link" && selection.id === link.id} onClick={() => onSelect({ kind: "link", id: link.id })}>
                            <span>
                                {names.get(link.source)} ↔ {names.get(link.target)}
                            </span>
                            <small>
                                {link.documents.length} shared {link.documents.length === 1 ? "document" : "documents"}
                            </small>
                        </button>
                    </li>
                ))}
            </ul>
            {!graph.links.length && <p>No shared-document connections match these filters.</p>}
            {shown < graph.links.length && (
                <button className="secondary-button" type="button" onClick={() => setShown((value) => value + 50)}>
                    Show more connections ({graph.links.length - shown} remaining)
                </button>
            )}
        </div>
    );
}

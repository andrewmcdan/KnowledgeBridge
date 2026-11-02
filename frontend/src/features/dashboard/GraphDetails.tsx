import type { Detail } from "../../types/models";
import type { RelatedDocument } from "../entities/entitiesApi";
import { ENTITY_COLORS, ENTITY_LABELS, type EntityGraph, type GraphSelection } from "./graphData";

interface GraphDetailsProps {
    graph: EntityGraph;
    selection: GraphSelection;
    onSelect: (selection: GraphSelection) => void;
    onOpenDetail: (detail: Detail) => void;
}

export function GraphDetails({ graph, selection, onSelect, onOpenDetail }: GraphDetailsProps) {
    const node = selection?.kind === "entity" ? graph.nodes.find((node) => node.id === selection.id) : undefined;
    const link = selection?.kind === "link" ? graph.links.find((link) => link.id === selection.id) : undefined;
    const byId = new Map(graph.nodes.map((node) => [node.id, node]));
    const connections = node ? graph.links.filter((link) => link.source === node.id || link.target === node.id) : [];
    const documents = link?.documents ?? node?.documents ?? [];
    return (
        <aside className="graph-inspector" aria-label="Selected graph details" aria-live="polite">
            <div className="graph-inspector-heading">
                <span className="graph-eyebrow">{link ? "Connection details" : node ? "Entity details" : "Explore a connection"}</span>
                {(node || link) && (
                    <button className="text-button" type="button" onClick={() => onSelect(null)}>
                        Clear selection
                    </button>
                )}
            </div>
            {node ? (
                <>
                    <h3>{node.name}</h3>
                    <span className="graph-entity-tag">
                        <i style={{ background: ENTITY_COLORS[node.type] }} />
                        {ENTITY_LABELS[node.type]} · {node.verified ? "Verified" : "Unverified"}
                    </span>
                    <p>{node.summary || "No summary available. Open a source document to check the context."}</p>
                    <h4>Connected entities ({connections.length})</h4>
                    {connections.length ? (
                        <ul className="graph-connection-list">
                            {connections.map((connection) => (
                                <li key={connection.id}>
                                    <button type="button" onClick={() => onSelect({ kind: "link", id: connection.id })}>
                                        <span>{byId.get(connection.source === node.id ? connection.target : connection.source)?.name}</span>
                                        <small>
                                            {connection.documents.length} shared {connection.documents.length === 1 ? "document" : "documents"}
                                        </small>
                                    </button>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <p>No connections in the current view.</p>
                    )}
                </>
            ) : link ? (
                <>
                    <h3>
                        {byId.get(link.source)?.name} <span className="graph-between">↔</span> {byId.get(link.target)?.name}
                    </h3>
                    <p>
                        These entities appear together in {documents.length} {documents.length === 1 ? "document" : "documents"}. Open a source to see how they are related.
                    </p>
                </>
            ) : (
                <div className="graph-selection-empty">
                    <span aria-hidden="true">◎</span>
                    <h3>Follow the evidence</h3>
                    <p>Select an entity to explore its neighbors, or a link to see every shared document behind that connection.</p>
                    <p>Use List view for keyboard-accessible entity and connection selection.</p>
                </div>
            )}
            {(node || link) && (
                <>
                    <h4>
                        {link ? "Shared documents" : "Source documents"} ({documents.length})
                    </h4>
                    <GraphDocuments documents={documents} onOpenDetail={onOpenDetail} />
                </>
            )}
        </aside>
    );
}

export function GraphDocuments({ documents, onOpenDetail }: { documents: RelatedDocument[]; onOpenDetail: (detail: Detail) => void }) {
    return documents.length ? (
        <ul className="graph-document-list">
            {documents.map((document) => (
                <li key={document.id}>
                    <button type="button" onClick={() => onOpenDetail({ id: document.id, title: document.title, text: "" })}>
                        <span>{document.title}</span>
                        <small>{document.type} · View document</small>
                    </button>
                </li>
            ))}
        </ul>
    ) : (
        <p>No source documents in this snapshot.</p>
    );
}

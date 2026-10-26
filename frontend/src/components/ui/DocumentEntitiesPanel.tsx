import type { DocumentEntities } from "../../features/entities/entitiesApi";
import { groupDocumentEntities } from "./documentPreview";

interface DocumentEntitiesPanelProps {
    entities: DocumentEntities | null;
    loading: boolean;
    error: string | null;
    busy: boolean;
    canExtract: boolean;
    hasDocument: boolean;
    onReload: () => void;
    onExtract: () => void;
    onOpenEntity: (id: number) => void;
}

export function DocumentEntitiesPanel({ entities, loading, error, busy, canExtract, hasDocument, onReload, onExtract, onOpenEntity }: DocumentEntitiesPanelProps) {
    const groups = groupDocumentEntities(entities?.entities ?? []);
    return (
        <details className="document-disclosure document-entities">
            <summary>
                Detected entities <span className="document-count">{entities?.entities.length ?? 0}</span>
            </summary>
            <div className="document-disclosure-body">
                {loading && <p role="status">Loading detected entities…</p>}
                {!hasDocument && <p>Entity information is unavailable for this preview.</p>}
                {error && (
                    <div className="document-feedback">
                        <p role="alert">{error}</p>
                        <button className="secondary-button" type="button" disabled={busy} onClick={onReload}>
                            Retry loading entities
                        </button>
                    </div>
                )}
                {entities && (
                    <>
                        <p>Detected references grouped by type. Unverified detections may contain mistakes; check the document for context. Showing up to 100 entities.</p>
                        {entities.status === "FAILED" && <p role="alert">Extraction could not complete ({entities.errorCode}). The document remains readable. An administrator can retry.</p>}
                        {entities.status === "NOT_EXTRACTED" && <p>This document has not been extracted yet.</p>}
                        {entities.status === "COMPLETED" && !entities.entities.length && <p>No entities were detected.</p>}
                        {entities.truncated && <p>Only some entities were extracted because the engine&apos;s detection limit was reached.</p>}
                        {groups.map((group) => (
                            <section className="document-entity-group" key={group.type} aria-label={group.label}>
                                <h3>
                                    {group.label} <span className="document-count">{group.entities.length}</span>
                                </h3>
                                <ul className="document-entity-grid">
                                    {group.entities.map((entity) => (
                                        <li key={entity.id}>
                                            <button className="document-entity-link" type="button" onClick={() => onOpenEntity(entity.id)}>
                                                {entity.name}
                                            </button>
                                            {entity.summary && <p>{entity.summary}</p>}
                                            <small>
                                                {entity.verified ? "Verified" : "Unverified"} · {entity.documentCount} {entity.documentCount === 1 ? "document" : "documents"}
                                            </small>
                                        </li>
                                    ))}
                                </ul>
                            </section>
                        ))}
                        {canExtract && (
                            <button className="secondary-button" type="button" disabled={busy || loading} onClick={onExtract}>
                                {busy ? "Extracting…" : entities.status === "NOT_EXTRACTED" ? "Extract entities" : "Retry entity extraction"}
                            </button>
                        )}
                    </>
                )}
            </div>
        </details>
    );
}

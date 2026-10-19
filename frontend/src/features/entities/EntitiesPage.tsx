import { useCallback, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { getEntities, getEntity, getEntityDocuments, type DetectedEntity, type RelatedDocument } from "./entitiesApi";
import type { Detail } from "../../types/models";
import { useEntityRefresh } from "./useEntityRefresh";

export function EntitiesPage({ onOpenDetail }: { onOpenDetail: (detail: Detail) => void }) {
    const [parameters, setParameters] = useSearchParams();
    const [name, setName] = useState("");
    const [type, setType] = useState("");
    const [limit, setLimit] = useState(25);
    const [entities, setEntities] = useState<DetectedEntity[]>([]);
    const [selected, setSelected] = useState<DetectedEntity | null>(null);
    const [documents, setDocuments] = useState<RelatedDocument[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const selectedId = parameters.get("entity");

    const load = useCallback(
        async (signal: AbortSignal, initial: boolean) => {
            if (initial) {
                setLoading(true);
                setError(null);
                setEntities([]);
                setSelected(null);
                setDocuments([]);
            }
            try {
                if (selectedId !== null) {
                    const id = Number(selectedId);
                    if (!Number.isSafeInteger(id) || id < 1) throw new Error("This entity link is invalid.");
                    const [entity, related] = await Promise.all([getEntity(id, signal), getEntityDocuments(id, signal)]);
                    if (!signal.aborted) {
                        setSelected(entity);
                        setDocuments(related);
                    }
                } else {
                    const result = await getEntities(name, type, limit, signal);
                    if (!signal.aborted) setEntities(result);
                }
                if (!signal.aborted) setError(null);
            } catch (reason: unknown) {
                if (!signal.aborted) setError(reason instanceof Error ? reason.message : "Entities could not be loaded.");
            } finally {
                if (!signal.aborted) setLoading(false);
            }
        },
        [name, type, limit, selectedId],
    );
    useEntityRefresh(load, 200);

    return (
        <section className="collection panel entity-browser" aria-busy={loading}>
            <h2>{selected?.name ?? "Detected entities"}</h2>
            <p className="knowledge-list-description">Automatically detected names and document labels are unverified and may contain mistakes. Open a source document to check the context.</p>
            {selectedId ? (
                <button className="panel-link" onClick={() => setParameters({})}>
                    Back to all entities
                </button>
            ) : (
                <div className="entity-filters">
                    <label>
                        Filter by name
                        <input type="search" maxLength={255} value={name} onChange={(event) => setName(event.target.value)} placeholder="Search people, projects, teams…" />
                    </label>
                    <label>
                        Type
                        <select value={type} onChange={(event) => setType(event.target.value)}>
                            <option value="">All types</option>
                            <option value="PERSON">People</option>
                            <option value="COMPANY">Companies</option>
                            <option value="PROJECT">Projects</option>
                            <option value="TEAM">Teams</option>
                        </select>
                    </label>
                    <label>
                        Show
                        <select value={limit} onChange={(event) => setLimit(Number(event.target.value))}>
                            <option value={25}>25 entities</option>
                            <option value={50}>50 entities</option>
                            <option value={100}>100 entities</option>
                        </select>
                    </label>
                </div>
            )}
            {error && (
                <p className="upload-error" role="alert">
                    {error}
                </p>
            )}
            {loading && <p role="status">Loading entities…</p>}
            {!loading && selected && (
                <>
                    <span className="preview-badge">{selected.type.toLowerCase()} · Unverified</span>
                    <p className="entity-summary">{selected.summary || "No summary is available. Check the related documents for context."}</p>
                    <h3>Related documents</h3>
                    {documents.map((document) => (
                        <button className="entity-card" key={document.id} onClick={() => onOpenDetail({ id: document.id, title: document.title, text: "" })}>
                            <strong>{document.title}</strong>
                            <span>{document.type} · Open document</span>
                        </button>
                    ))}
                    <p>Showing up to 100 related documents.</p>
                </>
            )}
            {!loading && selectedId === null && (
                <>
                    {entities.map((entity) => (
                        <button className="entity-card" key={entity.id} onClick={() => setParameters({ entity: String(entity.id) })}>
                            <strong>{entity.name}</strong>
                            <span>
                                {entity.type.toLowerCase()} · Unverified · {entity.documentCount} {entity.documentCount === 1 ? "document" : "documents"}
                            </span>
                        </button>
                    ))}
                    {!error && !entities.length && <p>No matching entities yet. New indexed documents are extracted automatically. Administrators can open an existing completed document and run extraction.</p>}
                    {entities.length === limit && <p>Showing the first {limit} matches. Narrow the filters or increase the limit.</p>}
                </>
            )}
        </section>
    );
}

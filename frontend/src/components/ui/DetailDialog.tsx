import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { Detail } from "../../types/models";
import { getDocumentContent, getDocumentEntities, type DocumentEntities } from "../../features/entities/entitiesApi";
import { useAuth } from "../../features/auth/useAuth";

export function DetailDialog({ detail, onClose }: { detail: Detail; onClose: () => void }) {
    const dialog = useRef<HTMLDialogElement>(null);
    const navigate = useNavigate();
    const { user } = useAuth();
    const [text, setText] = useState(detail.text);
    const [entities, setEntities] = useState<DocumentEntities | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const [loading, setLoading] = useState(detail.id !== undefined);
    useEffect(() => {
        dialog.current?.showModal();
    }, []);
    useEffect(() => {
        if (detail.id === undefined) return;
        let cancelled = false;
        Promise.all([getDocumentContent(detail.id), getDocumentEntities(detail.id)])
            .then(([content, detected]) => {
                if (!cancelled) {
                    setText(content.text);
                    setEntities(detected);
                }
            })
            .catch((reason: unknown) => {
                if (!cancelled) setError(reason instanceof Error ? reason.message : "The document could not be loaded.");
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [detail.id]);

    const extract = async () => {
        if (detail.id === undefined) return;
        setBusy(true);
        setError(null);
        try {
            setEntities(await getDocumentEntities(detail.id, true));
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "Extraction could not be completed.");
        } finally {
            setBusy(false);
        }
    };
    return (
        <dialog
            ref={dialog}
            aria-labelledby="detail-title"
            className="detail-modal"
            onClose={onClose}
            onClick={(event) => {
                if (event.target === event.currentTarget) dialog.current?.close();
            }}
        >
            <span className="preview-badge">Knowledge item</span>
            <h2 id="detail-title">{detail.title}</h2>
            {loading && <p role="status">Loading document…</p>}
            {error && (
                <p className="upload-error" role="alert">
                    {error}
                </p>
            )}
            {!loading && <p className="document-content">{text}</p>}
            {entities && (
                <section className="document-entities" aria-label="Document entities">
                    <h3>Detected entities</h3>
                    <p>Unverified detections may contain mistakes. Check the document for context. Showing up to 100 entities.</p>
                    {entities.status === "FAILED" && <p role="alert">Extraction could not complete ({entities.errorCode}). The document remains indexed. An administrator can retry.</p>}
                    {entities.status === "NOT_EXTRACTED" && <p>This document has not been extracted yet.</p>}
                    {entities.status === "COMPLETED" && !entities.entities.length && <p>No entities were detected.</p>}
                    {entities.truncated && <p>Only some entities were extracted because the engine's detection limit was reached.</p>}
                    <div className="entity-tags">
                        {entities.entities.map((entity) => (
                            <button
                                className="panel-link"
                                key={entity.id}
                                onClick={() => {
                                    onClose();
                                    navigate(`/entities?entity=${entity.id}`);
                                }}
                            >
                                {entity.name} · {entity.type.toLowerCase()}
                            </button>
                        ))}
                    </div>
                    {user?.role === "ADMIN" && (
                        <button className="panel-link" disabled={busy} onClick={() => void extract()}>
                            {busy ? "Extracting…" : entities.status === "NOT_EXTRACTED" ? "Extract entities" : "Retry entity extraction"}
                        </button>
                    )}
                </section>
            )}
            <button className="primary-button" autoFocus onClick={() => dialog.current?.close()}>
                Close
            </button>
        </dialog>
    );
}

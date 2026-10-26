import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { Detail } from "../../types/models";
import { getDocumentContent, getDocumentEntities, type DocumentEntities } from "../../features/entities/entitiesApi";
import { useAuth } from "../../features/auth/useAuth";
import { MarkdownDocument } from "./MarkdownDocument";
import { DocumentEntitiesPanel } from "./DocumentEntitiesPanel";
import { parseDocumentMarkdown } from "./documentPreview";

export function DetailDialog({ detail, onClose }: { detail: Detail; onClose: () => void }) {
    const dialog = useRef<HTMLDialogElement>(null);
    const navigate = useNavigate();
    const { user } = useAuth();
    const [document, setDocument] = useState({ title: detail.title, text: detail.text, type: "" });
    const [entities, setEntities] = useState<DocumentEntities | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const [loading, setLoading] = useState(detail.id !== undefined);
    const [entitiesLoading, setEntitiesLoading] = useState(detail.id !== undefined);
    const [entitiesError, setEntitiesError] = useState<string | null>(null);
    const parsed = useMemo(() => parseDocumentMarkdown(document.text), [document.text]);
    useEffect(() => {
        dialog.current?.showModal();
    }, []);
    useEffect(() => {
        if (detail.id === undefined) return;
        let cancelled = false;
        void getDocumentContent(detail.id)
            .then((content) => {
                if (!cancelled) setDocument(content);
            })
            .catch((reason: unknown) => {
                if (!cancelled) setError(reason instanceof Error ? reason.message : "The document could not be loaded.");
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        void getDocumentEntities(detail.id)
            .then((detected) => {
                if (!cancelled) setEntities(detected);
            })
            .catch((reason: unknown) => {
                if (!cancelled) setEntitiesError(reason instanceof Error ? reason.message : "Detected entities could not be loaded.");
            })
            .finally(() => {
                if (!cancelled) setEntitiesLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [detail.id]);

    const loadEntities = async (extract: boolean) => {
        if (detail.id === undefined) return;
        setBusy(true);
        setEntitiesError(null);
        try {
            setEntities(await getDocumentEntities(detail.id, extract));
        } catch (reason: unknown) {
            setEntitiesError(reason instanceof Error ? reason.message : "Detected entities could not be loaded.");
        } finally {
            setBusy(false);
        }
    };
    return (
        <dialog
            ref={dialog}
            aria-labelledby="detail-title"
            className="detail-modal document-dialog"
            onClose={onClose}
            onClick={(event) => {
                if (event.target === event.currentTarget) dialog.current?.close();
            }}
        >
            <header className="document-dialog-header">
                <button className="document-close" type="button" aria-label="Close document" autoFocus onClick={() => dialog.current?.close()}>
                    <span aria-hidden="true">×</span>
                </button>
                <div>
                    <span className="preview-badge">Document preview</span>
                    <h2 id="detail-title">{document.title}</h2>
                </div>
            </header>
            <div className="document-dialog-scroll">
                <div className="document-dialog-content">
                    <details className="document-disclosure document-metadata">
                        <summary>Metadata</summary>
                        <div className="document-disclosure-body">
                            <dl className="document-metadata-list">
                                {detail.id !== undefined && (
                                    <div>
                                        <dt>Document ID</dt>
                                        <dd>{detail.id}</dd>
                                    </div>
                                )}
                                {document.type && (
                                    <div>
                                        <dt>Type</dt>
                                        <dd>{document.type}</dd>
                                    </div>
                                )}
                                {parsed.metadata.map((entry, index) => (
                                    <div key={index}>
                                        <dt>{entry.label}</dt>
                                        <dd>{entry.value}</dd>
                                    </div>
                                ))}
                            </dl>
                            {!detail.id && !document.type && !parsed.rawMetadata && <p>No metadata is available for this document.</p>}
                            {parsed.metadataError && (
                                <>
                                    <p role="alert">{parsed.metadataError}</p>
                                    <pre className="document-raw-metadata">{parsed.rawMetadata}</pre>
                                </>
                            )}
                        </div>
                    </details>
                    <DocumentEntitiesPanel
                        entities={entities}
                        loading={entitiesLoading}
                        error={entitiesError}
                        busy={busy}
                        canExtract={user?.role === "ADMIN"}
                        hasDocument={detail.id !== undefined}
                        onReload={() => void loadEntities(false)}
                        onExtract={() => void loadEntities(true)}
                        onOpenEntity={(id) => {
                            onClose();
                            navigate(`/entities?entity=${id}`);
                        }}
                    />
                    {loading && (
                        <p className="document-feedback" role="status">
                            Loading document…
                        </p>
                    )}
                    {error && (
                        <p className="upload-error document-feedback" role="alert">
                            {error}
                        </p>
                    )}
                    {!loading && !error && <MarkdownDocument text={parsed.markdown} />}
                </div>
            </div>
        </dialog>
    );
}

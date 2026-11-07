import { useEffect, useRef, useState, type FormEvent } from "react";
import { knowledgeActionLabels, type KnowledgeAction, type KnowledgeItem } from "./knowledgeApi";

interface Props {
    item: KnowledgeItem;
    action: KnowledgeAction;
    busy: boolean;
    error: string | null;
    onConfirm: () => void;
    onCancel: () => void;
}

export function KnowledgeActionDialog({ item, action, busy, error, onConfirm, onCancel }: Props) {
    const dialog = useRef<HTMLDialogElement>(null);
    const [confirmation, setConfirmation] = useState("");
    const permanent = action === "permanent";
    useEffect(() => {
        dialog.current?.showModal();
    }, []);
    const descriptions: Record<KnowledgeAction, string> = {
        deactivate: "This document will remain in the knowledge base but will be excluded from search and AI answers. You can activate it again later.",
        activate: "Completed content will be reindexed and available for search and AI answers again. A failed document will still need an ingestion retry.",
        trash: "This document will move to Trash and be excluded from search and AI answers. Its content and metadata are retained for restoration.",
        restore: `This document will leave Trash and return to its previous ${item.trashedFrom === "INACTIVE" ? "inactive" : "active"} state.`,
        permanent:
            "This permanently removes the document, its retained upload and its associated metadata from KnowledgeBridge. It cannot be restored. Engine content is excluded from answers immediately; physical engine cleanup follows its retention process. Existing cached answers will be cleared.",
    };
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (!busy && (!permanent || confirmation === "DELETE")) onConfirm();
    };
    const cancel = () => {
        if (busy) return;
        dialog.current?.close();
        onCancel();
    };
    return (
        <dialog
            ref={dialog}
            className="knowledge-action-dialog"
            aria-labelledby="knowledge-action-title"
            aria-describedby="knowledge-action-description"
            onCancel={(event) => {
                event.preventDefault();
                cancel();
            }}
        >
            <form onSubmit={submit}>
                <h2 id="knowledge-action-title">{knowledgeActionLabels[action]} document</h2>
                <p className="knowledge-action-document">{item.title}</p>
                <p id="knowledge-action-description">{descriptions[action]}</p>
                {permanent && (
                    <label className="knowledge-confirm-label">
                        Type DELETE to confirm
                        <input value={confirmation} onChange={(event) => setConfirmation(event.target.value)} disabled={busy} autoComplete="off" />
                    </label>
                )}
                {error && (
                    <p className="upload-error" role="alert">
                        {error}
                    </p>
                )}
                {busy && <p role="status">Updating document…</p>}
                <div className="knowledge-action-buttons">
                    <button type="button" className="secondary-button" autoFocus disabled={busy} onClick={cancel}>
                        Cancel
                    </button>
                    <button type="submit" className={`primary-button ${permanent ? "lifecycle-danger" : ""}`} disabled={busy || (permanent && confirmation !== "DELETE")}>
                        {busy ? "Working…" : knowledgeActionLabels[action]}
                    </button>
                </div>
            </form>
        </dialog>
    );
}

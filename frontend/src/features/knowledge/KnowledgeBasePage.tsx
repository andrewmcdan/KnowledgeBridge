import { useEffect, useRef, useState } from "react";
import type { Detail } from "../../types/models";
import { DocumentRow } from "../../components/ui/DocumentRow";
import { changeKnowledgeLifecycle, getKnowledgeItems, knowledgeActionLabels, type KnowledgeAction, type KnowledgeItem, type KnowledgeLifecycle } from "./knowledgeApi";
import { useAuth } from "../auth/useAuth";
import { KnowledgeActionDialog } from "./KnowledgeActionDialog";
import { JiraSyncPanel } from "./JiraSyncPanel";

interface KnowledgeBasePageProps {
    search: string;
    onOpenDetail: (detail: Detail) => void;
}

export function KnowledgeBasePage({ search, onOpenDetail }: KnowledgeBasePageProps) {
    const { user } = useAuth();
    const admin = user?.role === "ADMIN";
    const [items, setItems] = useState<KnowledgeItem[] | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [filter, setFilter] = useState<KnowledgeLifecycle | "ALL">("ACTIVE");
    const [selected, setSelected] = useState<{ item: KnowledgeItem; action: KnowledgeAction } | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const actionLock = useRef(false);
    const mounted = useRef(true);
    useEffect(() => {
        mounted.current = true;
        let cancelled = false;
        getKnowledgeItems()
            .then((result) => {
                if (!cancelled) setItems(result);
            })
            .catch((reason: unknown) => {
                if (!cancelled) setError(reason instanceof Error ? reason.message : "We could not load the knowledge list.");
            });
        return () => {
            cancelled = true;
            mounted.current = false;
        };
    }, []);
    const refresh = async () => {
        setRefreshing(true);
        setError(null);
        try {
            const result = await getKnowledgeItems();
            if (mounted.current) setItems(result);
        } catch (reason: unknown) {
            if (mounted.current) {
                setItems(null);
                setError(reason instanceof Error ? reason.message : "Refresh failed. Try again.");
            }
        } finally {
            if (mounted.current) setRefreshing(false);
        }
    };
    const confirm = async () => {
        if (!admin || !selected || actionLock.current) return;
        actionLock.current = true;
        setBusy(true);
        setActionError(null);
        setNotice(null);
        try {
            await changeKnowledgeLifecycle(selected.item.id, selected.action);
            if (!mounted.current) return;
            setNotice(`${knowledgeActionLabels[selected.action]} completed for “${selected.item.title}”.`);
            setSelected(null);
            await refresh();
        } catch (reason: unknown) {
            if (mounted.current) setActionError(reason instanceof Error ? reason.message : "The document could not be updated.");
        } finally {
            actionLock.current = false;
            if (mounted.current) setBusy(false);
        }
    };
    const filtered = (items ?? []).filter(
        (item) => (admin ? filter === "ALL" || item.lifecycleState === filter : item.lifecycleState === "ACTIVE" && item.status === "COMPLETED") && `${item.title} ${item.type}`.toLowerCase().includes(search.toLowerCase()),
    );
    return (
        <section className="collection panel">
            <h2>Knowledge items</h2>
            <p className="knowledge-list-description">Only active, completed documents are used in search and AI answers. Administrators can deactivate documents or move them to Trash without losing their content.</p>
            <div className="knowledge-management-toolbar">
                {admin && (
                    <label>
                        Document state
                        <select value={filter} onChange={(event) => setFilter(event.target.value as KnowledgeLifecycle | "ALL")} disabled={busy || refreshing}>
                            <option value="ACTIVE">Active</option>
                            <option value="INACTIVE">Inactive</option>
                            <option value="TRASH">Trash</option>
                            <option value="ALL">All states</option>
                        </select>
                    </label>
                )}
                <button className="secondary-button" type="button" disabled={busy || refreshing} onClick={() => void refresh()}>
                    {refreshing ? "Refreshing…" : "Refresh list"}
                </button>
                {items && <span>{filtered.length} documents</span>}
            </div>
            {admin && <JiraSyncPanel onSynced={() => void refresh()} />}
            {notice && (
                <p className="upload-success" role="status">
                    {notice}
                </p>
            )}
            {!items && !error && <p role="status">Loading documents…</p>}
            {error && (
                <p className="upload-error" role="alert">
                    {error}
                </p>
            )}
            {filtered.map((item) => {
                const inProgress = item.status === "PENDING" || item.status === "PROCESSING";
                const actions: KnowledgeAction[] = item.lifecycleState === "TRASH" ? ["restore", "permanent"] : [item.lifecycleState === "ACTIVE" ? "deactivate" : "activate", "trash"];
                return (
                    <article key={item.id} className="knowledge-management-item" aria-label={item.title}>
                        <DocumentRow
                            document={{
                                id: item.id,
                                title: item.title,
                                type: item.type,
                                date: new Date(item.createdAt).toLocaleDateString(),
                                color: "blue",
                                text: item.lastErrorMessage ?? `Ingestion status: ${item.status.toLowerCase()}.`,
                                ingestionStatus: item.status,
                                lifecycleState: item.lifecycleState,
                            }}
                            onOpen={onOpenDetail}
                        />
                        {admin && (
                            <div className="knowledge-lifecycle-actions">
                                {actions.map((action) => (
                                    <button
                                        key={action}
                                        className={`secondary-button ${action === "permanent" ? "lifecycle-danger" : ""}`}
                                        type="button"
                                        disabled={busy || refreshing || inProgress}
                                        aria-label={`${knowledgeActionLabels[action]} ${item.title}`}
                                        onClick={() => {
                                            setActionError(null);
                                            setSelected({ item, action });
                                        }}
                                    >
                                        {knowledgeActionLabels[action]}
                                    </button>
                                ))}
                                {inProgress && <small>Management actions are available after ingestion finishes.</small>}
                            </div>
                        )}
                    </article>
                );
            })}
            {items && !error && !filtered.length && <p>{search ? `No knowledge items match “${search}”.` : "No documents in this view."}</p>}
            {admin && selected && (
                <KnowledgeActionDialog
                    item={selected.item}
                    action={selected.action}
                    busy={busy}
                    error={actionError}
                    onConfirm={() => void confirm()}
                    onCancel={() => {
                        if (!actionLock.current) setSelected(null);
                    }}
                />
            )}
        </section>
    );
}

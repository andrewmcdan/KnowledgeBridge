import { useEffect, useState } from "react";
import { getKnowledgeItems, type KnowledgeItem } from "../../features/knowledge/knowledgeApi";
import { Icon } from "../ui/Icon";

interface ContextColumnProps {
    onNavigate: (path: string) => void;
}

export function ContextColumn({ onNavigate }: ContextColumnProps) {
    const [recentItems, setRecentItems] = useState<KnowledgeItem[]>([]);
    const [ingestionError, setIngestionError] = useState(false);

    useEffect(() => {
        getKnowledgeItems()
            .then((items) => setRecentItems(items.sort((first, second) => Date.parse(second.createdAt) - Date.parse(first.createdAt)).slice(0, 3)))
            .catch(() => setIngestionError(true));
    }, []);

    return (
        <aside className="context-column" aria-label="Supporting knowledge">
            <section className="panel context-panel">
                <h2>
                    <Icon name="users" size={18} /> Detected Entities
                </h2>
                {/* TODO: Load detected entities for the current workspace from /api/entities. */}
                <p className="context-empty">Entities will appear after entity extraction is connected.</p>
                <button className="panel-link" onClick={() => onNavigate("/entities")}>
                    View all entities <Icon name="chevron" size={16} />
                </button>
            </section>
            <section className="panel context-panel">
                <h2>
                    <Icon name="file" size={18} /> Related Documents
                </h2>
                {/* TODO: Load related knowledge items for the active question from the live API. */}
                <p className="context-empty">Related documents will appear with live retrieval.</p>
                <button className="panel-link" onClick={() => onNavigate("/knowledge")}>
                    View all documents <Icon name="chevron" size={16} />
                </button>
            </section>
            <section className="panel context-panel upload-panel">
                <h2>
                    <Icon name="upload" size={18} /> Upload / Ingestion Status{" "}
                    <button className="text-button" onClick={() => onNavigate("/knowledge")}>
                        View all
                    </button>
                </h2>
                {ingestionError ? (
                    <p className="context-empty" role="alert">
                        Upload status could not be loaded.
                    </p>
                ) : recentItems.length ? (
                    recentItems.map((item) => (
                        <div className="upload-row" key={item.id}>
                            <Icon name="file" size={13} />
                            <span>{item.title}</span>
                            <strong className={`status-${item.status.toLowerCase()}`}>{item.status.toLowerCase()}</strong>
                        </div>
                    ))
                ) : (
                    <p className="context-empty">No documents have been uploaded yet.</p>
                )}
            </section>
        </aside>
    );
}

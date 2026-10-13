import { useEffect, useState } from "react";
import type { Detail } from "../../types/models";
import { DocumentRow } from "../../components/ui/DocumentRow";
import { getKnowledgeItems, type KnowledgeItem } from "./knowledgeApi";

interface KnowledgeBasePageProps {
    search: string;
    saved: string[];
    onOpenDetail: (detail: Detail) => void;
    onToggleSaved: (title: string) => void;
}

export function KnowledgeBasePage({ search, saved, onOpenDetail, onToggleSaved }: KnowledgeBasePageProps) {
    const [items, setItems] = useState<KnowledgeItem[]>([]);
    const [error, setError] = useState<string | null>(null);
    useEffect(() => {
        getKnowledgeItems()
            .then(setItems)
            .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "We could not load the knowledge list."));
    }, []);
    const filtered = items
        .filter((item) => `${item.title} ${item.type}`.toLowerCase().includes(search.toLowerCase()))
        .map((item) => ({
            title: item.title,
            type: item.type,
            date: new Date(item.createdAt).toLocaleDateString(),
            color: "blue" as const,
            text: item.lastErrorMessage ?? `Ingestion status: ${item.status.toLowerCase()}.`,
            ingestionStatus: item.status,
        }));
    return (
        <section className="collection panel">
            <h2>Knowledge items</h2>
            <p className="knowledge-list-description">Items are shown as they are stored. Only items marked completed will be available to Ask AI after ingestion is connected.</p>
            {error && (
                <p className="upload-error" role="alert">
                    {error}
                </p>
            )}
            {filtered.map((document) => (
                <DocumentRow key={`${document.title}-${document.date}`} document={document} saved={saved.includes(document.title)} onOpen={onOpenDetail} onToggleSaved={onToggleSaved} />
            ))}
            {!error && !filtered.length && <p>{search ? `No knowledge items match “${search}”.` : "No knowledge has been added yet. Use Uploads to add Markdown files or notes."}</p>}
        </section>
    );
}

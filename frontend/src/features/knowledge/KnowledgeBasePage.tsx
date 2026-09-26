import { documents } from "../../data/demoData";
import type { Detail } from "../../types/models";
import { DocumentRow } from "../../components/ui/DocumentRow";

interface KnowledgeBasePageProps {
    search: string;
    saved: string[];
    onOpenDetail: (detail: Detail) => void;
    onToggleSaved: (title: string) => void;
}

export function KnowledgeBasePage({ search, saved, onOpenDetail, onToggleSaved }: KnowledgeBasePageProps) {
    const filtered = documents.filter(document => `${document.title} ${document.type} ${document.text}`.toLowerCase().includes(search.toLowerCase()));
    return <section className="collection panel"><h2>Sample documents</h2>{filtered.map(document => <DocumentRow key={document.title} document={document} saved={saved.includes(document.title)} onOpen={onOpenDetail} onToggleSaved={onToggleSaved} />)}{!filtered.length && <p>No sample documents match “{search}”.</p>}</section>;
}

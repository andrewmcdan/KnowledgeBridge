import { documents, entities, uploadStatuses } from "../../data/demoData";
import type { Detail } from "../../types/models";
import { DocumentRow } from "../ui/DocumentRow";
import { Icon } from "../ui/Icon";

interface ContextColumnProps {
    saved: string[];
    onOpenDetail: (detail: Detail) => void;
    onToggleSaved: (title: string) => void;
    onNavigate: (path: string) => void;
}

export function ContextColumn({ saved, onOpenDetail, onToggleSaved, onNavigate }: ContextColumnProps) {
    return <aside className="context-column" aria-label="Supporting knowledge">
        <section className="panel context-panel"><h2><Icon name="users" size={18} /> Detected Entities</h2>{entities.map((entity, index) => <button key={entity} className="entity-row" onClick={() => onOpenDetail({ title: entity, text: `${entity} is part of the sample purchasing workflow. ${index === 3 ? "This cost center tracks software spending." : "This department reviews requests within its area of responsibility."}` })}><span className={`entity-icon color-${index}`}><Icon name={index === 3 ? "file" : "users"} size={19} /></span><span><strong>{entity}</strong><small>{index === 3 ? "Cost Center" : "Department"}</small></span></button>)}<button className="panel-link" onClick={() => onNavigate("/entities")}>View all entities <Icon name="chevron" size={16} /></button></section>
        <section className="panel context-panel"><h2><Icon name="file" size={18} /> Related Documents</h2>{documents.map(document => <DocumentRow key={document.title} document={document} saved={saved.includes(document.title)} onOpen={onOpenDetail} onToggleSaved={onToggleSaved} />)}<button className="panel-link" onClick={() => onNavigate("/knowledge")}>View all documents <Icon name="chevron" size={16} /></button></section>
        <section className="panel context-panel upload-panel"><h2><Icon name="upload" size={18} /> Upload / Ingestion Status <button className="text-button" onClick={() => onNavigate("/uploads")}>View all</button></h2>{uploadStatuses.map(([file, state]) => <div className="upload-row" key={file}><Icon name="file" size={13} /><span>{file}</span><strong className={state.toLowerCase()}>{state}</strong></div>)}</section>
    </aside>;
}

import { documents } from "../../data/demoData";
import type { Detail } from "../../types/models";
import { Icon } from "../../components/ui/Icon";

export function CitationList({ expanded, onToggle, onOpenDetail }: { expanded: boolean; onToggle: () => void; onOpenDetail: (detail: Detail) => void }) {
    return (
        <>
            <h3 className="citation-heading">Citations ({expanded ? 5 : 3})</h3>
            <div className="citations">
                {documents.slice(0, expanded ? 5 : 3).map((document, index) => (
                    <button className="citation" key={document.title} onClick={() => onOpenDetail({ title: document.title, text: document.text })}>
                        <span className={`file-icon ${document.color}`}>
                            <Icon name="file" size={23} />
                        </span>
                        <span>
                            <strong>{document.title}</strong>
                            <small>{document.type}</small>
                            <small className="updated">Updated {document.date}</small>
                        </span>
                        <span className="citation-number">{index + 1}</span>
                    </button>
                ))}
            </div>
            <button className="text-button" onClick={onToggle}>
                {expanded ? "Show fewer citations" : "View all citations"}
                <span>{expanded ? "⌃" : "⌄"}</span>
            </button>
        </>
    );
}

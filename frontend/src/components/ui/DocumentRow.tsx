import { Icon } from "./Icon";
import type { Detail, DocumentItem } from "../../types/models";

interface DocumentRowProps {
    document: DocumentItem;
    saved: boolean;
    onOpen: (detail: Detail) => void;
    onToggleSaved: (title: string) => void;
}

export function DocumentRow({ document, saved, onOpen, onToggleSaved }: DocumentRowProps) {
    return (
        <div className="document-row">
            <button className="document-open" onClick={() => onOpen({ title: document.title, text: document.text })}>
                <span className={`file-icon ${document.color}`}>
                    <Icon name="file" size={18} />
                </span>
                <span>
                    <strong>{document.title}</strong>
                    <small>
                        {document.type} <span>·</span> {document.date}
                    </small>
                </span>
            </button>
            <button className={`icon-button bookmark ${saved ? "saved" : ""}`} aria-label={`${saved ? "Unsave" : "Save"} ${document.title}`} aria-pressed={saved} onClick={() => onToggleSaved(document.title)}>
                <Icon name="bookmark" size={17} />
            </button>
        </div>
    );
}

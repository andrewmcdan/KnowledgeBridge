import { Icon } from "./Icon";
import type { Detail, DocumentItem } from "../../types/models";

interface DocumentRowProps {
    document: DocumentItem;
    onOpen: (detail: Detail) => void;
}

export function DocumentRow({ document, onOpen }: DocumentRowProps) {
    return (
        <div className="document-row">
            <button className="document-open" onClick={() => onOpen({ id: document.ingestionStatus === "COMPLETED" ? document.id : undefined, title: document.title, text: document.text, lifecycleState: document.lifecycleState })}>
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
            {document.ingestionStatus && <span className={`document-status status-${document.ingestionStatus.toLowerCase()}`}>{document.ingestionStatus === "COMPLETED" ? "Imported" : document.ingestionStatus.toLowerCase()}</span>}
            {document.lifecycleState && <span className={`document-status lifecycle-${document.lifecycleState.toLowerCase()}`}>{document.lifecycleState === "TRASH" ? "In Trash" : document.lifecycleState.toLowerCase()}</span>}
        </div>
    );
}

import { useEffect, useRef } from "react";
import type { Detail } from "../../types/models";

export function DetailDialog({ detail, onClose }: { detail: Detail; onClose: () => void }) {
    const dialog = useRef<HTMLDialogElement>(null);
    useEffect(() => { dialog.current?.showModal(); }, []);
    return <dialog ref={dialog} aria-labelledby="detail-title" className="detail-modal" onClose={onClose} onClick={event => { if (event.target === event.currentTarget) dialog.current?.close(); }}>
        <span className="preview-badge">Sample content</span><h2 id="detail-title">{detail.title}</h2><p>{detail.text}</p>
        <button className="primary-button" autoFocus onClick={() => dialog.current?.close()}>Close</button>
    </dialog>;
}

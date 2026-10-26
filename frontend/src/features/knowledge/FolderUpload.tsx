import { useEffect, useRef, useState, type FormEvent } from "react";
import { uploadKnowledgeFile, type KnowledgeItem } from "./knowledgeApi";
import { folderEntries, processFolderEntries, uploadTitle, type FolderUploadEntry } from "./uploadFiles";

export function FolderUpload({ onQueued, onBusyChange }: { onQueued: (item: KnowledgeItem) => void; onBusyChange: (busy: boolean) => void }) {
    const [entries, setEntries] = useState<FolderUploadEntry[]>([]);
    const [skipped, setSkipped] = useState(0);
    const [selected, setSelected] = useState(false);
    const [type, setType] = useState("Document");
    const [busy, setBusy] = useState(false);
    const [stopping, setStopping] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const stop = useRef(false);
    const mounted = useRef(true);
    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
            stop.current = true;
        };
    }, []);

    const update = (path: string, status: FolderUploadEntry["status"], error: string | null = null) => {
        if (mounted.current) setEntries((current) => current.map((entry) => (entry.path === path ? { ...entry, status, error } : entry)));
    };
    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (busy) return;
        if (!type.trim()) {
            setMessage("Enter a document type.");
            return;
        }
        setBusy(true);
        onBusyChange(true);
        setStopping(false);
        setMessage(null);
        stop.current = false;
        const { queued, failed } = await processFolderEntries(
            entries,
            async (entry) => {
                const item = await uploadKnowledgeFile(entry.file, uploadTitle(entry.file.name) || "Untitled", type.trim());
                if (mounted.current) onQueued(item);
            },
            update,
            () => stop.current,
        );
        if (mounted.current) {
            setBusy(false);
            onBusyChange(false);
            setMessage(`${queued} file(s) queued for ingestion; ${failed} upload(s) failed.${stop.current ? " Stopped; remaining files have not been sent." : ""}`);
        }
    };
    const queued = entries.filter((entry) => entry.status === "queued").length;
    const failed = entries.filter((entry) => entry.status === "failed").length;
    return (
        <form id="folder-upload-panel" className="upload-form" role="tabpanel" aria-labelledby="folder-upload-tab" onSubmit={(event) => void submit(event)}>
            <h2>Upload a folder</h2>
            <p>Choose a folder to find Markdown, Word, and video files in all its subfolders. Other files are skipped. Files are uploaded one at a time through the existing ingestion pipeline.</p>
            <p>Markdown: up to 1 MiB. Word: up to 10 MiB. Video: MP4, MOV, or WebM; server-configured size and duration limits apply. Videos may incur transcription charges. Only select content you intend to share with the workspace.</p>
            <label htmlFor="folder-document-type">Document type for these files</label>
            <input id="folder-document-type" maxLength={255} required value={type} disabled={busy} onChange={(event) => setType(event.target.value)} />
            <label className="file-picker">
                <span>Choose a folder</span>
                <input
                    type="file"
                    aria-label="Choose a folder"
                    {...{ webkitdirectory: "" }}
                    multiple
                    disabled={busy}
                    onChange={(event) => {
                        const files = Array.from(event.target.files ?? []);
                        if (!files.length) return;
                        const selection = folderEntries(files);
                        setEntries(selection.entries);
                        setSkipped(selection.skipped);
                        setSelected(true);
                        setMessage(null);
                    }}
                />
            </label>
            {selected && (
                <>
                    <p role="status">
                        {entries.length} eligible file(s), {skipped} unsupported file(s) skipped. {queued} queued; {failed} failed.
                    </p>
                    {!entries.length && <p>No eligible files were found in this folder.</p>}
                    <ul className="folder-upload-results" aria-label="Folder upload results">
                        {entries.slice(0, 100).map((entry) => (
                            <li key={entry.path}>
                                <span>{entry.path}</span> <strong>{entry.status}</strong>
                                {entry.error && <p className="ingestion-error">{entry.error}</p>}
                            </li>
                        ))}
                    </ul>
                    {entries.length > 100 && <p>Showing the first 100 files; all {entries.length} eligible files will be processed.</p>}
                </>
            )}
            <button className="primary-button" type="submit" disabled={busy || !entries.length || queued === entries.length}>
                {busy ? "Uploading folder…" : queued || failed ? "Upload remaining / retry failed" : "Upload folder"}
            </button>
            {busy && (
                <button
                    className="secondary-button"
                    type="button"
                    disabled={stopping}
                    onClick={() => {
                        stop.current = true;
                        setStopping(true);
                    }}
                >
                    {stopping ? "Stopping after current file…" : "Stop after current file"}
                </button>
            )}
            {message && <p role="status">{message}</p>}
            <p>Queued means accepted by the server, not fully processed. Follow ingestion results in the knowledge list below. Keep this tab open until uploads finish; selecting a folder again starts a new batch.</p>
        </form>
    );
}

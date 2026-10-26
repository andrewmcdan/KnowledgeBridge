import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Icon } from "../../components/ui/Icon";
import { createManualKnowledgeItem, getKnowledgeItems, retryKnowledgeItem, uploadKnowledgeFile, type KnowledgeItem } from "./knowledgeApi";
import { UPLOAD_ACCEPT, uploadTitle as titleFromFilename, validateUpload } from "./uploadFiles";
import { FolderUpload } from "./FolderUpload";

const MAX_CONTENT_BYTES = 1024 * 1024;
const POLLING_INTERVAL_MS = 10_000;

function contentSize(value: string): number {
    return new TextEncoder().encode(value).byteLength;
}

function isInProgress(item: KnowledgeItem): boolean {
    return item.status === "PENDING" || item.status === "PROCESSING";
}

function validateTextFields(title: string, type: string, text: string): string | null {
    if (!title.trim()) return "Enter a title.";
    if (!type.trim()) return "Enter a document type.";
    if (!text.trim()) return "Enter content before adding the note.";
    if (contentSize(text) > MAX_CONTENT_BYTES) return "Content must not exceed 1 MiB.";
    return null;
}

export function UploadsPage() {
    const [items, setItems] = useState<KnowledgeItem[] | null>(null);
    const [uploadMode, setUploadMode] = useState<"markdown" | "note" | "folder">("markdown");
    const [file, setFile] = useState<File | null>(null);
    const [uploadTitle, setUploadTitle] = useState("");
    const [uploadType, setUploadType] = useState("Document");
    const [noteTitle, setNoteTitle] = useState("");
    const [noteType, setNoteType] = useState("Note");
    const [content, setContent] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [refreshing, setRefreshing] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [folderBusy, setFolderBusy] = useState(false);
    const [retryingId, setRetryingId] = useState<number | null>(null);
    const fileInput = useRef<HTMLInputElement>(null);

    const loadItems = useCallback(async () => {
        try {
            setItems(await getKnowledgeItems());
        } catch (reason: unknown) {
            setItems((current) => current ?? []);
            setError(reason instanceof Error ? reason.message : "We could not load the knowledge list.");
        }
    }, []);

    useEffect(() => {
        let cancelled = false;

        const loadInitialItems = async () => {
            try {
                const initialItems = await getKnowledgeItems();
                if (!cancelled) setItems(initialItems);
            } catch (reason: unknown) {
                if (cancelled) return;
                setItems([]);
                setError(reason instanceof Error ? reason.message : "We could not load the knowledge list.");
            }
        };

        void loadInitialItems();
        return () => {
            cancelled = true;
        };
    }, []);

    useEffect(() => {
        if (!items?.some(isInProgress)) return;

        const timer = window.setInterval(() => {
            void loadItems();
        }, POLLING_INTERVAL_MS);
        return () => window.clearInterval(timer);
    }, [items, loadItems]);

    const addItem = (item: KnowledgeItem, action: string) => {
        setItems((current) => [item, ...(current ?? []).filter((currentItem) => currentItem.id !== item.id)]);
        setNotice(`“${item.title}” ${action}.`);
    };

    const selectFile = (selectedFile: File | null) => {
        setFile(selectedFile);
        if (!selectedFile || uploadTitle.trim()) return;
        setUploadTitle(titleFromFilename(selectedFile.name));
    };

    const submitFile = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!file) {
            setError("Choose a Markdown, Word, or video file to upload.");
            return;
        }
        const fileError = validateUpload(file);
        if (fileError) {
            setError(fileError);
            return;
        }
        const validationError = validateTextFields(uploadTitle, uploadType, "file");
        if (validationError) {
            setError(validationError);
            return;
        }

        setSubmitting(true);
        setError(null);
        setNotice(null);
        try {
            addItem(await uploadKnowledgeFile(file, uploadTitle.trim(), uploadType.trim()), "was added and queued for ingestion");
            setFile(null);
            setUploadTitle("");
            if (fileInput.current) fileInput.current.value = "";
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "We could not upload the file.");
        } finally {
            setSubmitting(false);
        }
    };

    const submitNote = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const validationError = validateTextFields(noteTitle, noteType, content);
        if (validationError) {
            setError(validationError);
            return;
        }

        setSubmitting(true);
        setError(null);
        setNotice(null);
        try {
            addItem(await createManualKnowledgeItem(noteTitle.trim(), noteType.trim(), content), "was added and queued for ingestion");
            setNoteTitle("");
            setNoteType("Note");
            setContent("");
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "We could not save the note.");
        } finally {
            setSubmitting(false);
        }
    };

    const retry = async (item: KnowledgeItem) => {
        setRetryingId(item.id);
        setError(null);
        setNotice(null);
        try {
            addItem(await retryKnowledgeItem(item.id), "was queued for another ingestion attempt");
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "We could not retry ingestion.");
        } finally {
            setRetryingId(null);
        }
    };

    return (
        <section className="uploads-page">
            <p className="uploads-intro">Add Markdown, Word, or video files, folders, or notes for everyone in the workspace. Ingestion status refreshes automatically while work is in progress.</p>
            <section className="panel upload-pane" aria-label="Add knowledge">
                <div className="upload-tabs" role="tablist" aria-label="Upload type">
                    <button
                        id="markdown-upload-tab"
                        type="button"
                        role="tab"
                        aria-selected={uploadMode === "markdown"}
                        aria-controls="markdown-upload-panel"
                        disabled={submitting || folderBusy}
                        className={uploadMode === "markdown" ? "active" : ""}
                        onClick={() => setUploadMode("markdown")}
                    >
                        <Icon name="upload" size={16} /> Upload file
                    </button>
                    <button
                        id="manual-note-tab"
                        type="button"
                        role="tab"
                        disabled={submitting || folderBusy}
                        aria-selected={uploadMode === "note"}
                        aria-controls="manual-note-panel"
                        className={uploadMode === "note" ? "active" : ""}
                        onClick={() => setUploadMode("note")}
                    >
                        <Icon name="file" size={16} /> Manual note
                    </button>
                    <button
                        id="folder-upload-tab"
                        type="button"
                        role="tab"
                        aria-selected={uploadMode === "folder"}
                        aria-controls="folder-upload-panel"
                        disabled={submitting || folderBusy}
                        className={uploadMode === "folder" ? "active" : ""}
                        onClick={() => setUploadMode("folder")}
                    >
                        <Icon name="upload" size={16} /> Upload folder
                    </button>
                </div>
                {uploadMode === "markdown" ? (
                    <form id="markdown-upload-panel" className="upload-form" role="tabpanel" aria-labelledby="markdown-upload-tab" onSubmit={submitFile}>
                        <h2>Upload a document</h2>
                        <p>
                            Upload a UTF-8 <code>.md</code> file (up to 1 MiB), a <code>.docx</code> file (up to 10 MiB), or an MP4, MOV, or WebM video. Video size and duration limits are configured on the server; transcription may incur
                            charges.
                        </p>
                        <label htmlFor="upload-title">Title</label>
                        <input id="upload-title" value={uploadTitle} maxLength={255} required onChange={(event) => setUploadTitle(event.target.value)} />
                        <label htmlFor="upload-type">Document type</label>
                        <input id="upload-type" value={uploadType} maxLength={255} required onChange={(event) => setUploadType(event.target.value)} />
                        <label className="file-picker">
                            <span>{file?.name ?? "Choose a Markdown, Word, or video file"}</span>
                            <input ref={fileInput} type="file" accept={UPLOAD_ACCEPT} onChange={(event) => selectFile(event.target.files?.[0] ?? null)} />
                        </label>
                        <button className="primary-button" type="submit" disabled={submitting}>
                            {submitting ? "Adding…" : "Add document"}
                        </button>
                    </form>
                ) : uploadMode === "folder" ? (
                    <FolderUpload onQueued={(item) => addItem(item, "was added and queued for ingestion")} onBusyChange={setFolderBusy} />
                ) : (
                    <form id="manual-note-panel" className="upload-form" role="tabpanel" aria-labelledby="manual-note-tab" onSubmit={submitNote}>
                        <h2>Create manual note</h2>
                        <label htmlFor="note-title">Title</label>
                        <input id="note-title" value={noteTitle} maxLength={255} required onChange={(event) => setNoteTitle(event.target.value)} />
                        <label htmlFor="note-type">Document type</label>
                        <input id="note-type" value={noteType} maxLength={255} required onChange={(event) => setNoteType(event.target.value)} />
                        <label htmlFor="note-content">Note</label>
                        <textarea id="note-content" value={content} maxLength={MAX_CONTENT_BYTES} required onChange={(event) => setContent(event.target.value)} />
                        <button className="primary-button" type="submit" disabled={submitting}>
                            {submitting ? "Adding…" : "Add note"}
                        </button>
                    </form>
                )}
            </section>
            {error && (
                <p className="upload-message upload-error" role="alert">
                    {error}
                </p>
            )}
            {notice && (
                <p className="upload-message upload-success" role="status">
                    {notice}
                </p>
            )}
            <section className="collection panel">
                <div className="collection-heading">
                    <div>
                        <h2>Knowledge list</h2>
                        <p>Refreshes every 10 seconds while an item is pending or processing.</p>
                    </div>
                    <button
                        className="secondary-button"
                        type="button"
                        onClick={() => {
                            setRefreshing(true);
                            void loadItems().finally(() => setRefreshing(false));
                        }}
                        disabled={items === null || refreshing}
                    >
                        {items === null || refreshing ? "Refreshing…" : "Refresh"}
                    </button>
                </div>
                {items === null ? (
                    <p>Loading knowledge items…</p>
                ) : items.length ? (
                    items.map((item) => (
                        <article className="knowledge-item" key={item.id}>
                            <div>
                                <strong>{item.title}</strong>
                                <p>
                                    {item.type} · {item.characterCount.toLocaleString()} characters
                                </p>
                                {item.lastErrorMessage && <p className="ingestion-error">{item.lastErrorMessage}</p>}
                            </div>
                            <div className="knowledge-item-actions">
                                <span className={`status-${item.status.toLowerCase()}`}>{item.status.toLowerCase()}</span>
                                {item.status === "FAILED" && (
                                    <button className="secondary-button" type="button" disabled={retryingId === item.id} onClick={() => void retry(item)}>
                                        {retryingId === item.id ? "Retrying…" : "Retry"}
                                    </button>
                                )}
                            </div>
                        </article>
                    ))
                ) : (
                    <p>No knowledge has been added yet.</p>
                )}
            </section>
        </section>
    );
}

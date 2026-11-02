import { lazy, Suspense, useCallback, useEffect, useRef, useState, type DragEvent, type FormEvent } from "react";
import { Icon } from "../../components/ui/Icon";
import { createManualKnowledgeItem, getKnowledgeItems, retryKnowledgeItem, uploadKnowledgeFile, type KnowledgeItem } from "./knowledgeApi";
import { UPLOAD_ACCEPT, uploadDocumentType, uploadTitle as titleFromFilename, validateUpload } from "./uploadFiles";
import { FolderUpload } from "./FolderUpload";
import { NoteEditor } from "./NoteEditor";
import { validateManualNote as validateTextFields } from "./manualNotes";

const POLLING_INTERVAL_MS = 10_000;
const MANUAL_NOTE_TYPE = "Manual note";
const RichTextEditor = lazy(async () => ({
    default: (await import("./RichTextEditor")).RichTextEditor,
}));

function isInProgress(item: KnowledgeItem): boolean {
    return item.status === "PENDING" || item.status === "PROCESSING";
}

export function UploadsPage() {
    const [items, setItems] = useState<KnowledgeItem[] | null>(null);
    const [uploadMode, setUploadMode] = useState<"markdown" | "note" | "folder">("markdown");
    const [file, setFile] = useState<File | null>(null);
    const [uploadTitle, setUploadTitle] = useState("");
    const [titleEdited, setTitleEdited] = useState(false);
    const [useAiTitle, setUseAiTitle] = useState(false);
    const [noteTitle, setNoteTitle] = useState("");
    const [content, setContent] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [refreshing, setRefreshing] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [folderBusy, setFolderBusy] = useState(false);
    const [retryingId, setRetryingId] = useState<number | null>(null);
    const [editingId, setEditingId] = useState<number | null>(null);
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

    const addItem = (item: KnowledgeItem) => {
        setItems((current) => [item, ...(current ?? []).filter((currentItem) => currentItem.id !== item.id)]);
        setNotice(
            item.status === "FAILED" ? `“${item.title}” was saved, but ingestion failed. Review the error and retry.` : item.status === "COMPLETED" ? `“${item.title}” finished ingestion.` : `“${item.title}” was queued for ingestion.`,
        );
    };

    const selectFile = (selectedFile: File | null) => {
        setFile(selectedFile);
        if (!titleEdited || !uploadTitle.trim()) {
            setUploadTitle(selectedFile ? titleFromFilename(selectedFile.name) : "");
            setTitleEdited(false);
        }
    };

    const dropFile = (event: DragEvent<HTMLLabelElement>) => {
        event.preventDefault();
        if (!submitting) selectFile(event.dataTransfer.files?.[0] ?? null);
    };

    const submitFile = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!file) {
            setError("Choose a Markdown, Word, PDF, or video file to upload.");
            return;
        }
        const fileError = validateUpload(file);
        if (fileError) {
            setError(fileError);
            return;
        }
        const title = uploadTitle.trim() || (useAiTitle ? titleFromFilename(file.name) : "");
        const type = uploadDocumentType(file.name);
        if (type === null) {
            setError("Choose a Markdown, Word, PDF, or video file to upload.");
            return;
        }
        const validationError = validateTextFields(title, type, "file");
        if (validationError) {
            setError(validationError);
            return;
        }

        setSubmitting(true);
        setError(null);
        setNotice(null);
        try {
            addItem(await uploadKnowledgeFile(file, title, type, useAiTitle));
            setFile(null);
            setUploadTitle("");
            setTitleEdited(false);
            if (fileInput.current) fileInput.current.value = "";
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "We could not upload the file.");
        } finally {
            setSubmitting(false);
        }
    };

    const submitNote = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const validationError = validateTextFields(noteTitle, MANUAL_NOTE_TYPE, content);
        if (validationError) {
            setError(validationError);
            return;
        }

        setSubmitting(true);
        setError(null);
        setNotice(null);
        try {
            addItem(await createManualKnowledgeItem(noteTitle.trim(), MANUAL_NOTE_TYPE, content));
            setNoteTitle("");
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
            addItem(await retryKnowledgeItem(item.id));
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "We could not retry ingestion.");
        } finally {
            setRetryingId(null);
        }
    };

    return (
        <section className="uploads-page">
            <header className="knowledge-upload-header">
                <h1>
                    <Icon name="upload" size={27} /> Add knowledge
                </h1>
                <p>Add Markdown, Word, PDF, or video files, folders, or notes for everyone in the workspace. Ingestion status refreshes automatically while work is in progress.</p>
            </header>
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
                        <span className="upload-tab-icon">
                            <Icon name="file" size={23} />
                        </span>
                        <span>
                            <strong>File upload</strong>
                            <small>Add a document or video from your device.</small>
                        </span>
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
                        <span className="upload-tab-icon">
                            <Icon name="upload" size={23} />
                        </span>
                        <span>
                            <strong>Folder upload</strong>
                            <small>Import supported files from a folder.</small>
                        </span>
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
                        <span className="upload-tab-icon">
                            <Icon name="file" size={23} />
                        </span>
                        <span>
                            <strong>Manual note</strong>
                            <small>Create knowledge directly from text.</small>
                        </span>
                    </button>
                </div>
                <div className={`upload-workspace upload-workspace-${uploadMode}`}>
                    {uploadMode === "markdown" ? (
                        <form id="markdown-upload-panel" className="upload-form" role="tabpanel" aria-labelledby="markdown-upload-tab" onSubmit={submitFile}>
                            <label className="file-picker upload-file-picker" onDragOver={(event) => event.preventDefault()} onDrop={dropFile}>
                                <span className="upload-file-picker-icon">
                                    <Icon name="upload" size={26} />
                                </span>
                                <span className="upload-file-picker-copy">
                                    <strong>{file ? "Replace selected file" : "Drop a file here or browse"}</strong>
                                    <small>Markdown, DOCX, PDF, MP4, MOV, or WebM</small>
                                </span>
                                <span className="upload-file-picker-action">{file ? "Choose another file" : "Browse files"}</span>
                                {file && (
                                    <span className="upload-file-picker-details">
                                        <Icon name="file" size={18} />
                                        <span>
                                            <strong>{file.name}</strong>
                                            <small>{uploadDocumentType(file.name)} detected</small>
                                        </span>
                                    </span>
                                )}
                                <input ref={fileInput} type="file" accept={UPLOAD_ACCEPT} disabled={submitting} onChange={(event) => selectFile(event.target.files?.[0] ?? null)} />
                            </label>
                            <p className="upload-file-help">Markdown files support up to 1 MiB; DOCX and text-based PDFs support up to 10 MiB. Scanned PDFs are not supported. Video limits are configured on the server.</p>
                            <div className="upload-title-field">
                                <label htmlFor="upload-title">Document title</label>
                                <input
                                    id="upload-title"
                                    value={uploadTitle}
                                    maxLength={255}
                                    required={!useAiTitle}
                                    disabled={submitting}
                                    aria-describedby="upload-title-help"
                                    onChange={(event) => {
                                        setUploadTitle(event.target.value);
                                        setTitleEdited(true);
                                    }}
                                />
                            </div>
                            <div className="upload-form-footer">
                                <div>
                                    <label className="ai-title-option">
                                        <input type="checkbox" checked={useAiTitle} disabled={submitting} onChange={(event) => setUseAiTitle(event.target.checked)} />
                                        Use AI to name this file
                                    </label>
                                    <p id="upload-title-help">{useAiTitle ? "AI will generate the final title after processing; usage may incur charges." : "The filename provides the initial title."}</p>
                                </div>
                                <button className="primary-button upload-submit-button" type="submit" disabled={submitting}>
                                    {submitting ? "Ingesting…" : "Add to knowledge"}
                                </button>
                            </div>
                        </form>
                    ) : uploadMode === "folder" ? (
                        <FolderUpload onQueued={addItem} onBusyChange={setFolderBusy} />
                    ) : (
                        <form id="manual-note-panel" className="upload-form" role="tabpanel" aria-labelledby="manual-note-tab" onSubmit={submitNote}>
                            <h2>Create manual note</h2>
                            <p>Capture knowledge in your own words. Use the formatting controls to organize headings, lists, and emphasis.</p>
                            <label htmlFor="note-title">Title</label>
                            <input id="note-title" value={noteTitle} maxLength={255} required onChange={(event) => setNoteTitle(event.target.value)} />
                            <label htmlFor="note-content">Note</label>
                            <Suspense
                                fallback={
                                    <p className="rich-text-editor-loading" role="status">
                                        Loading editor…
                                    </p>
                                }
                            >
                                <RichTextEditor value={content} disabled={submitting} onChange={setContent} />
                            </Suspense>
                            <div className="manual-note-footer">
                                <small>Saved as a manual note. Markdown formatting is preserved.</small>
                                <button className="primary-button" type="submit" disabled={submitting}>
                                    {submitting ? "Adding…" : "Add note"}
                                </button>
                            </div>
                        </form>
                    )}
                </div>
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
                                {item.ingestionStage === "GENERATING_TITLE" && <p role="status">Generating AI title…</p>}
                                {item.lastErrorMessage && <p className="ingestion-error">{item.lastErrorMessage}</p>}
                            </div>
                            <div className="knowledge-item-actions">
                                <span className={`status-${item.status.toLowerCase()}`}>{item.status.toLowerCase()}</span>
                                {item.sourceKind === "MANUAL" && !isInProgress(item) && (
                                    <button className="secondary-button" type="button" disabled={editingId !== null || submitting || folderBusy || retryingId !== null} onClick={() => setEditingId(item.id)}>
                                        Edit note
                                    </button>
                                )}
                                {item.status === "FAILED" && (
                                    <button className="secondary-button" type="button" disabled={retryingId === item.id || editingId === item.id} onClick={() => void retry(item)}>
                                        {retryingId === item.id ? "Retrying…" : "Retry"}
                                    </button>
                                )}
                            </div>
                            {editingId === item.id && (
                                <NoteEditor
                                    id={item.id}
                                    onCancel={() => setEditingId(null)}
                                    onSaved={(updated) => {
                                        addItem(updated);
                                        setEditingId(null);
                                    }}
                                />
                            )}
                        </article>
                    ))
                ) : (
                    <p>No knowledge has been added yet.</p>
                )}
            </section>
        </section>
    );
}

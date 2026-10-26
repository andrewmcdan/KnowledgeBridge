import { useEffect, useState, type FormEvent } from "react";
import { getManualNote, updateManualNote, type KnowledgeItem, type ManualNote } from "./knowledgeApi";
import { MAX_CONTENT_BYTES, validateManualNote } from "./manualNotes";

export function NoteEditor({ id, onSaved, onCancel }: { id: number; onSaved: (item: KnowledgeItem) => void; onCancel: () => void }) {
    const [note, setNote] = useState<ManualNote | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const [reload, setReload] = useState(0);
    useEffect(() => {
        let active = true;
        void getManualNote(id)
            .then((value) => {
                if (active) setNote(value);
            })
            .catch((reason: unknown) => {
                if (active) setError(reason instanceof Error ? reason.message : "We could not load this note.");
            });
        return () => {
            active = false;
        };
    }, [id, reload]);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!note || saving) return;
        const validation = validateManualNote(note.title, note.type, note.text);
        if (validation) {
            setError(validation);
            return;
        }
        setSaving(true);
        setError(null);
        try {
            onSaved(await updateManualNote(note));
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "We could not save this note.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <form className="upload-form note-editor" aria-label="Edit manual note" onSubmit={(event) => void submit(event)}>
            <h3>Edit note</h3>
            <p>Saving replaces this note in the knowledge engine and refreshes its search index.</p>
            {!note && !error && <p role="status">Loading note…</p>}
            {note && (
                <>
                    <label htmlFor={`edit-title-${id}`}>Note title</label>
                    <input id={`edit-title-${id}`} value={note.title} required maxLength={255} disabled={saving} onChange={(event) => setNote({ ...note, title: event.target.value })} />
                    <label htmlFor={`edit-type-${id}`}>Note type</label>
                    <input id={`edit-type-${id}`} value={note.type} required maxLength={255} disabled={saving} onChange={(event) => setNote({ ...note, type: event.target.value })} />
                    <label htmlFor={`edit-text-${id}`}>Note content</label>
                    <textarea id={`edit-text-${id}`} value={note.text} required maxLength={MAX_CONTENT_BYTES} disabled={saving} onChange={(event) => setNote({ ...note, text: event.target.value })} />
                    <button className="primary-button" type="submit" disabled={saving}>
                        {saving ? "Saving and reindexing…" : "Save and reindex"}
                    </button>
                </>
            )}
            {error && (
                <p className="upload-error" role="alert">
                    {error}
                </p>
            )}
            {error && (
                <button
                    className="secondary-button"
                    type="button"
                    disabled={saving}
                    onClick={() => {
                        setNote(null);
                        setError(null);
                        setReload((value) => value + 1);
                    }}
                >
                    Reload saved note
                </button>
            )}
            <button className="secondary-button" type="button" disabled={saving} onClick={onCancel}>
                Cancel editing
            </button>
        </form>
    );
}

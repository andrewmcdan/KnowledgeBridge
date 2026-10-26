export const MAX_CONTENT_BYTES = 1024 * 1024;

export function validateManualNote(title: string, type: string, text: string): string | null {
    if (!title.trim()) return "Enter a title.";
    if (!type.trim()) return "Enter a document type.";
    if (!text.trim()) return "Enter content before saving the note.";
    if (new TextEncoder().encode(text.trimEnd() + "\n").byteLength > MAX_CONTENT_BYTES) return "Content must not exceed 1 MiB.";
    return null;
}

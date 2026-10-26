export const UPLOAD_ACCEPT = ".md,.docx,.mp4,.mov,.webm";

export function uploadKind(name: string): "document" | "video" | null {
    if (/\.(md|docx)$/i.test(name)) return "document";
    if (/\.(mp4|mov|webm)$/i.test(name)) return "video";
    return null;
}

export function uploadDocumentType(name: string): "Markdown" | "Word document" | "Video" | null {
    if (/\.md$/i.test(name)) return "Markdown";
    if (/\.docx$/i.test(name)) return "Word document";
    if (/\.(mp4|mov|webm)$/i.test(name)) return "Video";
    return null;
}

export function uploadTitle(name: string): string {
    return (
        (name.split(/[\\/]/).pop() ?? "")
            .normalize("NFKC")
            .replace(/\.(md|docx|mp4|mov|webm)$/i, "")
            .replace(/_+|\.{2,}|-{2,}/g, " ")
            .replace(/[^\p{L}\p{M}\p{N}\s.'’()&+-]/gu, " ")
            .replace(/\s+/g, " ")
            .replace(/^[\s.-]+|[\s.-]+$/g, "")
            .slice(0, 255)
            .trim() || "Untitled"
    );
}

export function validateUpload(file: Pick<File, "name" | "size">): string | null {
    if (!uploadKind(file.name)) return "Only Markdown (.md), Word (.docx), and video (.mp4, .mov, .webm) files can be uploaded.";
    if (!file.size) return "Choose a file with content.";
    if (file.name.length > 255) return "Filenames must not exceed 255 characters.";
    if (/\.md$/i.test(file.name) && file.size > 1024 * 1024) return "Markdown content must not exceed 1 MiB.";
    if (/\.docx$/i.test(file.name) && file.size > 10 * 1024 * 1024) return "Word files must not exceed 10 MiB.";
    return null; // Video size/duration limits are configurable and enforced by the backend.
}

export interface FolderUploadEntry {
    file: File;
    path: string;
    status: "ready" | "uploading" | "queued" | "failed";
    error: string | null;
}

export function folderEntries(files: File[]): { entries: FolderUploadEntry[]; skipped: number } {
    const eligible = files.filter((file) => uploadKind(file.name) !== null);
    return {
        entries: eligible.map((file): FolderUploadEntry => ({ file, path: file.webkitRelativePath || file.name, status: "ready", error: null })).sort((first, second) => first.path.localeCompare(second.path)),
        skipped: files.length - eligible.length,
    };
}

export async function processFolderEntries(
    entries: FolderUploadEntry[],
    upload: (entry: FolderUploadEntry) => Promise<void>,
    update: (path: string, status: FolderUploadEntry["status"], error?: string | null) => void,
    shouldStop: () => boolean,
): Promise<{ queued: number; failed: number }> {
    let queued = 0;
    let failed = 0;
    for (const entry of entries.filter((entry) => entry.status !== "queued")) {
        if (shouldStop()) break;
        const validation = validateUpload(entry.file);
        if (validation) {
            update(entry.path, "failed", validation);
            failed++;
            continue;
        }
        update(entry.path, "uploading");
        try {
            await upload(entry);
            update(entry.path, "queued");
            queued++;
        } catch (reason: unknown) {
            update(entry.path, "failed", reason instanceof Error ? reason.message : "Upload failed.");
            failed++;
        }
    }
    return { queued, failed };
}

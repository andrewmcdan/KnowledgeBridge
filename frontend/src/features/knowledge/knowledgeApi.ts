export type IngestionStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export interface KnowledgeItem {
    id: number;
    title: string;
    type: string;
    ownerId: number;
    status: IngestionStatus;
    characterCount: number;
    externalEngineId: string | null;
    lastErrorCode: string | null;
    lastErrorMessage: string | null;
    createdAt: string;
    updatedAt: string;
}

export function getAuthorizationHeader(): Record<string, string> {
    const token = sessionStorage.getItem("knowledgebridge.auth.token") ?? localStorage.getItem("knowledgebridge.auth.token");
    if (!token) throw new Error("Your sign-in session is no longer valid.");
    return { Authorization: `Bearer ${token}` };
}

function isIngestionStatus(value: unknown): value is IngestionStatus {
    return value === "PENDING" || value === "PROCESSING" || value === "COMPLETED" || value === "FAILED";
}

function isKnowledgeItem(value: unknown): value is KnowledgeItem {
    if (typeof value !== "object" || value === null) return false;
    const item = value as Record<string, unknown>;
    return (
        typeof item.id === "number" &&
        typeof item.title === "string" &&
        typeof item.type === "string" &&
        typeof item.ownerId === "number" &&
        isIngestionStatus(item.status) &&
        typeof item.characterCount === "number" &&
        (typeof item.externalEngineId === "string" || item.externalEngineId === null) &&
        (typeof item.lastErrorCode === "string" || item.lastErrorCode === null) &&
        (typeof item.lastErrorMessage === "string" || item.lastErrorMessage === null) &&
        typeof item.createdAt === "string" &&
        typeof item.updatedAt === "string"
    );
}

export async function readError(response: Response, fallback: string): Promise<Error> {
    if (response.status === 401) return new Error("Your sign-in session is no longer valid.");
    if (response.status === 403) return new Error("You do not have permission to manage knowledge items.");
    if (response.status === 404) return new Error("This knowledge item no longer exists.");
    if (response.status === 409) return new Error("Only failed ingestion attempts can be retried.");
    if (response.status === 413) return new Error("Content must not exceed 1 MiB.");
    if (response.status === 415) return new Error("Only Markdown (.md) files can be uploaded.");

    const message = await response.text();
    return new Error(message || fallback);
}

async function readKnowledgeItem(response: Response): Promise<KnowledgeItem> {
    const payload: unknown = await response.json();
    if (!isKnowledgeItem(payload)) throw new Error("The server returned an invalid knowledge item.");
    return payload;
}

export async function getKnowledgeItems(): Promise<KnowledgeItem[]> {
    const response = await fetch("/api/knowledge-items", { headers: getAuthorizationHeader() });
    if (!response.ok) throw await readError(response, "We could not load the knowledge list.");

    const payload: unknown = await response.json();
    if (!Array.isArray(payload) || !payload.every(isKnowledgeItem)) throw new Error("The server returned an invalid knowledge list.");
    return payload;
}

export async function createManualKnowledgeItem(title: string, type: string, text: string): Promise<KnowledgeItem> {
    const response = await fetch("/api/knowledge-items/manual", {
        method: "POST",
        headers: { ...getAuthorizationHeader(), "Content-Type": "application/json" },
        body: JSON.stringify({ title, type, text }),
    });
    if (!response.ok) throw await readError(response, "We could not save the note.");
    return readKnowledgeItem(response);
}

export async function uploadMarkdown(file: File, title: string, type: string): Promise<KnowledgeItem> {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("title", title);
    formData.append("type", type);
    const response = await fetch("/api/knowledge-items/upload", {
        method: "POST",
        headers: getAuthorizationHeader(),
        body: formData,
    });
    if (!response.ok) throw await readError(response, "We could not upload the Markdown file.");
    return readKnowledgeItem(response);
}

export async function retryKnowledgeItem(id: number): Promise<KnowledgeItem> {
    const response = await fetch(`/api/knowledge-items/${id}/retry-ingestion`, {
        method: "POST",
        headers: getAuthorizationHeader(),
    });
    if (!response.ok) throw await readError(response, "We could not retry ingestion.");
    return readKnowledgeItem(response);
}

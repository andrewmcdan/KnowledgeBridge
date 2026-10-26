import { readApiError } from "../../api/apiError";
import { uploadKind } from "./uploadFiles";

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

export async function readError(response: Response, fallback: string, requestAuthorization?: string): Promise<Error> {
    return readApiError(response, fallback, requestAuthorization);
}

async function readKnowledgeItem(response: Response): Promise<KnowledgeItem> {
    const payload: unknown = await response.json();
    if (!isKnowledgeItem(payload)) throw new Error("The server returned an invalid knowledge item.");
    return payload;
}

export async function getKnowledgeItems(): Promise<KnowledgeItem[]> {
    const headers = getAuthorizationHeader();
    const response = await fetch("/api/knowledge-items", { headers });
    if (!response.ok) throw await readError(response, "We could not load the knowledge list.", headers.Authorization);

    const payload: unknown = await response.json();
    if (!Array.isArray(payload) || !payload.every(isKnowledgeItem)) throw new Error("The server returned an invalid knowledge list.");
    return payload;
}

export async function createManualKnowledgeItem(title: string, type: string, text: string): Promise<KnowledgeItem> {
    const headers = getAuthorizationHeader();
    const response = await fetch("/api/knowledge-items/manual", {
        method: "POST",
        headers: { ...headers, "Content-Type": "application/json" },
        body: JSON.stringify({ title, type, text }),
    });
    if (!response.ok) throw await readError(response, "We could not save the note.", headers.Authorization);
    return readKnowledgeItem(response);
}

export async function uploadKnowledgeFile(file: File, title: string, type: string): Promise<KnowledgeItem> {
    const kind = uploadKind(file.name);
    if (kind === null) throw new Error("This file type is not supported.");
    const headers = getAuthorizationHeader();
    const formData = new FormData();
    formData.append("file", file);
    formData.append("title", title);
    formData.append("type", type);
    const response = await fetch(`/api/knowledge-items/${kind === "video" ? "video" : "upload"}`, {
        method: "POST",
        headers,
        body: formData,
    });
    if (!response.ok) throw await readError(response, "We could not upload the file. Word files must be readable .docx documents with text.", headers.Authorization);
    return readKnowledgeItem(response);
}

export async function retryKnowledgeItem(id: number): Promise<KnowledgeItem> {
    const headers = getAuthorizationHeader();
    const response = await fetch(`/api/knowledge-items/${id}/retry-ingestion`, {
        method: "POST",
        headers,
    });
    if (!response.ok) throw await readError(response, "We could not retry ingestion.", headers.Authorization);
    return readKnowledgeItem(response);
}

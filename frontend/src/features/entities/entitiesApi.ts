import { getAuthorizationHeader } from "../knowledge/knowledgeApi";
import { readApiError } from "../../api/apiError";
import { ENTITIES_UPDATED_EVENT } from "./useEntityRefresh";

export type EntityType = "PERSON" | "COMPANY" | "PROJECT" | "TEAM";

export interface DetectedEntity {
    id: number;
    name: string;
    type: EntityType;
    summary: string;
    documentCount: number;
    verified: boolean;
}

export interface RelatedDocument {
    id: number;
    title: string;
    type: string;
}

export interface DocumentEntities {
    status: "NOT_EXTRACTED" | "COMPLETED" | "FAILED";
    truncated: boolean;
    errorCode: string | null;
    entities: DetectedEntity[];
}

function isEntity(value: unknown): value is DetectedEntity {
    if (typeof value !== "object" || value === null) return false;
    const entity = value as Record<string, unknown>;
    return (
        Number.isSafeInteger(entity.id) &&
        typeof entity.name === "string" &&
        typeof entity.type === "string" &&
        ["PERSON", "COMPANY", "PROJECT", "TEAM"].includes(entity.type) &&
        typeof entity.summary === "string" &&
        typeof entity.documentCount === "number" &&
        typeof entity.verified === "boolean"
    );
}

function isDocument(value: unknown): value is RelatedDocument {
    if (typeof value !== "object" || value === null) return false;
    const document = value as Record<string, unknown>;
    return Number.isSafeInteger(document.id) && typeof document.title === "string" && typeof document.type === "string";
}

async function request(path: string, method = "GET", signal?: AbortSignal): Promise<unknown> {
    const headers = getAuthorizationHeader();
    const response = await fetch(path, { method, headers, signal, cache: "no-store" });
    if (!response.ok) throw await readApiError(response, "We could not load entities. Please try again.", headers.Authorization);
    return response.json();
}

export async function getEntities(name = "", type = "", limit = 25, signal?: AbortSignal): Promise<DetectedEntity[]> {
    const parameters = new URLSearchParams({ name, type, limit: String(limit) });
    const result = await request(`/api/entities?${parameters}`, "GET", signal);
    if (!Array.isArray(result) || !result.every(isEntity)) throw new Error("The server returned invalid entity data.");
    return result;
}

export async function getEntity(id: number, signal?: AbortSignal): Promise<DetectedEntity> {
    const result = await request(`/api/entities/${id}`, "GET", signal);
    if (!isEntity(result)) throw new Error("The server returned an invalid entity.");
    return result;
}

export async function getEntityDocuments(id: number, signal?: AbortSignal): Promise<RelatedDocument[]> {
    const result = await request(`/api/entities/${id}/documents?limit=100`, "GET", signal);
    if (!Array.isArray(result) || !result.every(isDocument)) throw new Error("The server returned invalid related documents.");
    return result;
}

export async function getDocumentEntities(id: number, extract = false): Promise<DocumentEntities> {
    const result = await request(`/api/knowledge-items/${id}/${extract ? "extract-entities" : "entities?limit=100"}`, extract ? "POST" : "GET");
    if (typeof result !== "object" || result === null) throw new Error("The server returned invalid document entities.");
    const data = result as Record<string, unknown>;
    if (
        typeof data.status !== "string" ||
        !["NOT_EXTRACTED", "COMPLETED", "FAILED"].includes(data.status) ||
        typeof data.truncated !== "boolean" ||
        !(data.errorCode === null || typeof data.errorCode === "string") ||
        !Array.isArray(data.entities) ||
        !data.entities.every(isEntity)
    )
        throw new Error("The server returned invalid document entities.");
    if (extract) window.dispatchEvent(new Event(ENTITIES_UPDATED_EVENT));
    return { status: data.status as DocumentEntities["status"], truncated: data.truncated, errorCode: data.errorCode, entities: data.entities };
}

export async function getDocumentContent(id: number): Promise<RelatedDocument & { text: string }> {
    const result = await request(`/api/knowledge-items/${id}/content`);
    if (!isDocument(result) || !("text" in result) || typeof result.text !== "string") throw new Error("The server returned invalid document content.");
    return result as RelatedDocument & { text: string };
}

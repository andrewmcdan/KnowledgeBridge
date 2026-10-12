export type AskAiMode = "Search" | "Synthesis";

export interface SearchHit {
    itemKey: string;
    externalId: string;
    title: string;
    documentType: string;
    snippet: string;
    score: number;
}

export interface SearchResponse {
    hits: SearchHit[];
    retrieval: {
        vectorEnabled: boolean;
        projectionReady: boolean;
        degraded: boolean;
    };
}

export interface SynthesisResponse {
    answer: string;
    aiGenerated: boolean;
    extractiveFallback: boolean;
    citations: Array<{
        itemKey: string;
        externalId: string;
        title: string | null;
        documentType: string | null;
    }>;
    gaps: string[];
    warnings: string[];
    usage: {
        model: string;
        inputTokens: number | null;
        outputTokens: number | null;
        estimatedCostUsd: number | null;
    } | null;
}

export type QueryResult = { mode: "Search"; response: SearchResponse } | { mode: "Synthesis"; response: SynthesisResponse };

function getAuthorizationHeader(): Record<string, string> {
    const token = sessionStorage.getItem("knowledgebridge.auth.token") ?? localStorage.getItem("knowledgebridge.auth.token");
    if (!token) throw new Error("Your sign-in session is no longer valid.");
    return { Authorization: `Bearer ${token}` };
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null;
}

function isStringArray(value: unknown): value is string[] {
    return Array.isArray(value) && value.every((item) => typeof item === "string");
}

function isSearchResponse(value: unknown): value is SearchResponse {
    if (!isRecord(value) || !Array.isArray(value.hits) || !isRecord(value.retrieval)) return false;

    const retrieval = value.retrieval;
    return (
        typeof retrieval.vectorEnabled === "boolean" &&
        typeof retrieval.projectionReady === "boolean" &&
        typeof retrieval.degraded === "boolean" &&
        value.hits.every(
            (hit) =>
                isRecord(hit) &&
                typeof hit.itemKey === "string" &&
                typeof hit.externalId === "string" &&
                typeof hit.title === "string" &&
                typeof hit.documentType === "string" &&
                typeof hit.snippet === "string" &&
                typeof hit.score === "number",
        )
    );
}

function isSynthesisResponse(value: unknown): value is SynthesisResponse {
    if (!isRecord(value) || !Array.isArray(value.citations) || !isStringArray(value.gaps) || !isStringArray(value.warnings)) return false;

    const usage = value.usage;
    const validUsage =
        usage === null ||
        (isRecord(usage) &&
            typeof usage.model === "string" &&
            (typeof usage.inputTokens === "number" || usage.inputTokens === null) &&
            (typeof usage.outputTokens === "number" || usage.outputTokens === null) &&
            (typeof usage.estimatedCostUsd === "number" || usage.estimatedCostUsd === null));

    return (
        typeof value.answer === "string" &&
        typeof value.aiGenerated === "boolean" &&
        typeof value.extractiveFallback === "boolean" &&
        validUsage &&
        value.citations.every(
            (citation) =>
                isRecord(citation) &&
                typeof citation.itemKey === "string" &&
                typeof citation.externalId === "string" &&
                (typeof citation.title === "string" || citation.title === null) &&
                (typeof citation.documentType === "string" || citation.documentType === null),
        )
    );
}

async function readError(response: Response): Promise<Error> {
    const payload: unknown = await response.json().catch(() => null);
    if (isRecord(payload) && typeof payload.message === "string") return new Error(payload.message);
    if (response.status === 401) return new Error("Your sign-in session is no longer valid.");
    if (response.status === 403) return new Error("You do not have permission to ask questions.");
    return new Error("We could not complete your question. Please try again.");
}

export async function submitQuestion(mode: AskAiMode, question: string): Promise<QueryResult> {
    const endpoint = mode === "Search" ? "/api/query/search" : "/api/query/synthesize";
    const response = await fetch(endpoint, {
        method: "POST",
        headers: { ...getAuthorizationHeader(), "Content-Type": "application/json" },
        body: JSON.stringify({ question }),
    });
    if (!response.ok) throw await readError(response);

    const payload: unknown = await response.json();
    if (mode === "Search" && isSearchResponse(payload)) return { mode, response: payload };
    if (mode === "Synthesis" && isSynthesisResponse(payload)) return { mode, response: payload };
    throw new Error("The server returned an invalid query response.");
}

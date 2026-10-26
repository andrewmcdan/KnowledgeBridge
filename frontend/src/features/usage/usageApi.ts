import { readApiError } from "../../api/apiError";
import { getAuthorizationHeader } from "../knowledge/knowledgeApi";

export interface UsageTotals {
    scope: "personal" | "workspace";
    queryCount: number;
    inputTokens: number;
    outputTokens: number;
    totalTokens: number;
    cacheHits: number;
    estimatedQueryCount: number;
}

export async function getUsageTotals(): Promise<UsageTotals> {
    const headers = getAuthorizationHeader();
    const response = await fetch("/api/usage", { headers });
    if (!response.ok) throw await readApiError(response, "We could not load usage totals.", headers.Authorization);
    const payload: unknown = await response.json();
    if (typeof payload !== "object" || payload === null) throw new Error("The server returned invalid usage totals.");
    const value = payload as Record<string, unknown>;
    if (
        (value.scope !== "personal" && value.scope !== "workspace") ||
        !["queryCount", "inputTokens", "outputTokens", "totalTokens", "cacheHits", "estimatedQueryCount"].every((key) => typeof value[key] === "number" && Number.isSafeInteger(value[key]) && value[key] >= 0)
    ) {
        throw new Error("The server returned invalid usage totals.");
    }
    return value as unknown as UsageTotals;
}

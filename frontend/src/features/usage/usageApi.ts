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

export interface DailyUsageTrend {
    date: string;
    queryCount: number;
    inputTokens: number;
    outputTokens: number;
    totalTokens: number;
    cacheHits: number;
    estimatedQueryCount: number;
    estimatedCostUsd: number;
    hasEstimatedCost: boolean;
}

export interface ModelUsageTrend {
    model: string;
    queryCount: number;
    inputTokens: number;
    outputTokens: number;
    totalTokens: number;
    estimatedCostUsd: number;
    hasEstimatedCost: boolean;
}

export interface UsageTrends {
    scope: "personal" | "workspace";
    startDate: string;
    endDate: string;
    daily: DailyUsageTrend[];
    models: ModelUsageTrend[];
    estimatedCostUsd: number;
    hasEstimatedCost: boolean;
}

function isNonNegativeInteger(value: unknown): value is number {
    return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function isNonNegativeNumber(value: unknown): value is number {
    return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

export async function getUsageTotals(): Promise<UsageTotals> {
    const headers = getAuthorizationHeader();
    const response = await fetch("/api/usage", { headers });
    if (!response.ok) throw await readApiError(response, "We could not load usage totals.", headers.Authorization);
    const payload: unknown = await response.json();
    if (typeof payload !== "object" || payload === null) throw new Error("The server returned invalid usage totals.");
    const value = payload as Record<string, unknown>;
    if ((value.scope !== "personal" && value.scope !== "workspace") || !["queryCount", "inputTokens", "outputTokens", "totalTokens", "cacheHits", "estimatedQueryCount"].every((key) => isNonNegativeInteger(value[key]))) {
        throw new Error("The server returned invalid usage totals.");
    }
    return value as unknown as UsageTotals;
}

function isDailyUsageTrend(value: unknown): value is DailyUsageTrend {
    if (typeof value !== "object" || value === null) return false;
    const trend = value as Record<string, unknown>;
    return (
        typeof trend.date === "string" &&
        ["queryCount", "inputTokens", "outputTokens", "totalTokens", "cacheHits", "estimatedQueryCount"].every((key) => isNonNegativeInteger(trend[key])) &&
        isNonNegativeNumber(trend.estimatedCostUsd) &&
        typeof trend.hasEstimatedCost === "boolean"
    );
}

function isModelUsageTrend(value: unknown): value is ModelUsageTrend {
    if (typeof value !== "object" || value === null) return false;
    const trend = value as Record<string, unknown>;
    return (
        typeof trend.model === "string" &&
        ["queryCount", "inputTokens", "outputTokens", "totalTokens"].every((key) => isNonNegativeInteger(trend[key])) &&
        isNonNegativeNumber(trend.estimatedCostUsd) &&
        typeof trend.hasEstimatedCost === "boolean"
    );
}

export async function getUsageTrends(): Promise<UsageTrends> {
    const headers = getAuthorizationHeader();
    const response = await fetch("/api/usage/trends", { headers });
    if (!response.ok) throw await readApiError(response, "We could not load usage trends.", headers.Authorization);
    const payload: unknown = await response.json();
    if (typeof payload !== "object" || payload === null) throw new Error("The server returned invalid usage trends.");
    const value = payload as Record<string, unknown>;
    if (
        (value.scope !== "personal" && value.scope !== "workspace") ||
        typeof value.startDate !== "string" ||
        typeof value.endDate !== "string" ||
        !Array.isArray(value.daily) ||
        !value.daily.every(isDailyUsageTrend) ||
        !Array.isArray(value.models) ||
        !value.models.every(isModelUsageTrend) ||
        !isNonNegativeNumber(value.estimatedCostUsd) ||
        typeof value.hasEstimatedCost !== "boolean"
    ) {
        throw new Error("The server returned invalid usage trends.");
    }
    return value as unknown as UsageTrends;
}

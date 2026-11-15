import { getAuthorizationHeader, readError } from "./knowledgeApi";

const BASE_URL = "/api/integrations/jira";

export interface JiraConnectionStatus {
    configured: boolean;
    reachable: boolean;
}

export interface JiraProject {
    key: string;
    name: string;
}

export type JiraSyncRunStatus = "RUNNING" | "COMPLETED" | "FAILED";

export interface JiraSyncRun {
    id: number;
    projectKey: string;
    status: JiraSyncRunStatus;
    finishedAt: string | null;
    createdCount: number;
    updatedCount: number;
    unchangedCount: number;
    failedCount: number;
    errorMessage: string | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null;
}

function isConnectionStatus(value: unknown): value is JiraConnectionStatus {
    return isRecord(value) && typeof value.configured === "boolean" && typeof value.reachable === "boolean";
}

function isProject(value: unknown): value is JiraProject {
    return isRecord(value) && typeof value.key === "string" && typeof value.name === "string";
}

function isSyncRun(value: unknown): value is JiraSyncRun {
    return (
        isRecord(value) &&
        typeof value.id === "number" &&
        typeof value.projectKey === "string" &&
        (value.status === "RUNNING" || value.status === "COMPLETED" || value.status === "FAILED") &&
        (typeof value.finishedAt === "string" || value.finishedAt === null) &&
        typeof value.createdCount === "number" &&
        typeof value.updatedCount === "number" &&
        typeof value.unchangedCount === "number" &&
        typeof value.failedCount === "number" &&
        (typeof value.errorMessage === "string" || value.errorMessage === null)
    );
}

async function request<T>(path: string, method: string, fallback: string, isValid: (payload: unknown) => payload is T): Promise<T> {
    const headers = getAuthorizationHeader();
    const response = await fetch(`${BASE_URL}${path}`, { method, headers });
    if (!response.ok) throw await readError(response, fallback, headers.Authorization);
    const payload: unknown = await response.json();
    if (!isValid(payload)) throw new Error("The server returned an invalid Jira response.");
    return payload;
}

function isListOf<T>(isItem: (value: unknown) => value is T) {
    return (payload: unknown): payload is T[] => Array.isArray(payload) && payload.every(isItem);
}

export function getJiraStatus(): Promise<JiraConnectionStatus> {
    return request("/status", "GET", "We could not check the Jira connection.", isConnectionStatus);
}

export function getJiraProjects(): Promise<JiraProject[]> {
    return request("/projects", "GET", "We could not load Jira projects.", isListOf(isProject));
}

export function getJiraSyncRuns(): Promise<JiraSyncRun[]> {
    return request("/sync-runs", "GET", "We could not load Jira sync history.", isListOf(isSyncRun));
}

export function getJiraSyncRun(id: number): Promise<JiraSyncRun> {
    return request(`/sync-runs/${id}`, "GET", "We could not check the Jira sync.", isSyncRun);
}

export function startJiraSync(projectKey: string): Promise<JiraSyncRun> {
    return request(`/projects/${encodeURIComponent(projectKey)}/sync`, "POST", "We could not start the Jira sync.", isSyncRun);
}

export function describeJiraSyncRun(run: JiraSyncRun): string {
    const counts = `${run.createdCount} added, ${run.updatedCount} updated, ${run.unchangedCount} unchanged, ${run.failedCount} failed`;
    if (run.status === "RUNNING") return `Syncing ${run.projectKey}: ${counts} so far…`;
    if (run.status === "FAILED") return `Sync of ${run.projectKey} stopped: ${run.errorMessage ?? "unknown error"} (${counts}).`;
    return `Last sync of ${run.projectKey} finished ${run.finishedAt ? new Date(run.finishedAt).toLocaleString() : ""}: ${counts}.`;
}

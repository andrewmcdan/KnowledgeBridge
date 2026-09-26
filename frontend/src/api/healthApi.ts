export type HealthStatus = "API & database connected" | "Platform needs attention" | "Backend unavailable";

interface HealthResponse {
    status: string;
    database: string;
}

export async function fetchHealthStatus(signal: AbortSignal): Promise<HealthStatus> {
    const response = await fetch("/api/health", { signal });
    if (!response.ok) throw new Error(`Health request failed with status ${response.status}`);
    const data = (await response.json()) as HealthResponse;
    return data.status === "UP" && data.database === "UP" ? "API & database connected" : "Platform needs attention";
}

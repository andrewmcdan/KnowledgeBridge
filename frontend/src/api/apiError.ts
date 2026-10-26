export const AUTH_EXPIRED_EVENT = "knowledgebridge:auth-expired";

/** Reads the committed {code, message} envelope without displaying HTML or raw server errors. */
export async function readApiError(response: Response, fallback: string, requestAuthorization?: string): Promise<Error> {
    if (response.status === 401 && requestAuthorization) window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT, { detail: requestAuthorization }));
    const payload: unknown = await response.json().catch(() => null);
    if (typeof payload === "object" && payload !== null && "code" in payload && typeof payload.code === "string" && "message" in payload && typeof payload.message === "string" && payload.message.trim()) {
        return new Error(payload.message);
    }
    return new Error(fallback);
}

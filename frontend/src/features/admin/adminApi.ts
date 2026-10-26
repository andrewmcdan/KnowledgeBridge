import { readApiError } from "../../api/apiError";
import { getAuthorizationHeader } from "../knowledge/knowledgeApi";
import type { AuthUser } from "../auth/authApi";

export interface ManagedUser extends AuthUser {
    enabled: boolean;
}

function isManagedUser(value: unknown): value is ManagedUser {
    if (typeof value !== "object" || value === null) return false;
    const user = value as Record<string, unknown>;
    return (
        typeof user.id === "number" &&
        Number.isSafeInteger(user.id) &&
        user.id > 0 &&
        typeof user.email === "string" &&
        typeof user.displayName === "string" &&
        (user.role === "USER" || user.role === "ADMIN") &&
        typeof user.enabled === "boolean"
    );
}

export async function getUsers(): Promise<ManagedUser[]> {
    const headers = getAuthorizationHeader();
    const response = await fetch("/api/admin/users", { headers });
    if (!response.ok) throw await readApiError(response, "We could not load users. Please try again.", headers.Authorization);
    const payload: unknown = await response.json();
    if (!Array.isArray(payload) || !payload.every(isManagedUser)) throw new Error("The server returned an invalid user list.");
    return payload;
}

export async function promoteUser(id: number): Promise<ManagedUser> {
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error("Select a valid user.");
    const headers = getAuthorizationHeader();
    const response = await fetch(`/api/admin/users/${id}/promote`, { method: "POST", headers });
    if (!response.ok) throw await readApiError(response, "We could not grant admin access. Please try again.", headers.Authorization);
    const payload: unknown = await response.json();
    if (!isManagedUser(payload) || payload.id !== id || payload.role !== "ADMIN" || !payload.enabled) throw new Error("The server returned an invalid promotion response.");
    return payload;
}

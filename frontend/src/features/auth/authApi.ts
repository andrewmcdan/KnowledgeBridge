import { readApiError } from "../../api/apiError";

export type UserRole = "ADMIN" | "USER";

export interface AuthUser {
    id: number;
    email: string;
    displayName: string;
    role: UserRole;
}

export interface LoginResponse {
    token: string;
    expiresAt: string;
    user: AuthUser;
}

export async function register(displayName: string, email: string, password: string): Promise<LoginResponse> {
    const response = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ displayName: displayName.trim(), email: email.trim(), password }),
    });
    if (!response.ok) throw await readApiError(response, "We could not create your account. Please try again.");
    const payload: unknown = await response.json();
    if (!isLoginResponse(payload) || payload.user.role !== "USER") throw new Error("The server returned an invalid registration response.");
    return payload;
}

function isAuthUser(value: unknown): value is AuthUser {
    if (typeof value !== "object" || value === null) return false;

    const user = value as Record<string, unknown>;
    return typeof user.id === "number" && typeof user.email === "string" && typeof user.displayName === "string" && (user.role === "ADMIN" || user.role === "USER");
}

function isLoginResponse(value: unknown): value is LoginResponse {
    if (typeof value !== "object" || value === null) return false;

    const response = value as Record<string, unknown>;
    return typeof response.token === "string" && response.token.length > 0 && typeof response.expiresAt === "string" && isAuthUser(response.user);
}

export async function login(email: string, password: string): Promise<LoginResponse> {
    const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
    });

    if (response.status === 401) throw new Error("The email address or password is incorrect.");
    if (!response.ok) throw await readApiError(response, "We could not sign you in. Please try again.");

    const payload: unknown = await response.json();
    if (!isLoginResponse(payload)) throw new Error("The server returned an invalid sign-in response.");

    return payload;
}

export async function getCurrentUser(token: string): Promise<AuthUser> {
    const response = await fetch("/api/auth/me", {
        headers: { Authorization: `Bearer ${token}` },
    });

    if (!response.ok) throw await readApiError(response, "Your sign-in session is no longer valid.", `Bearer ${token}`);

    const payload: unknown = await response.json();
    if (!isAuthUser(payload)) throw new Error("The server returned an invalid user profile.");

    return payload;
}

export async function logout(token: string): Promise<void> {
    const response = await fetch("/api/auth/logout", {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) throw await readApiError(response, "The server could not acknowledge sign-out.");
}

export type UserRole = "ADMIN" | "USER";

export interface AuthUser {
    id: number;
    email: string;
    displayName: string;
    role: UserRole;
}

interface LoginResponse {
    token: string;
    expiresAt: string;
    user: AuthUser;
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
    if (!response.ok) throw new Error("We could not sign you in. Please try again.");

    const payload: unknown = await response.json();
    if (!isLoginResponse(payload)) throw new Error("The server returned an invalid sign-in response.");

    return payload;
}

export async function getCurrentUser(token: string): Promise<AuthUser> {
    const response = await fetch("/api/auth/me", {
        headers: { Authorization: `Bearer ${token}` },
    });

    if (!response.ok) throw new Error("Your sign-in session is no longer valid.");

    const payload: unknown = await response.json();
    if (!isAuthUser(payload)) throw new Error("The server returned an invalid user profile.");

    return payload;
}

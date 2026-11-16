const SESSION_TOKEN_KEY = "knowledgebridge.auth.token";

export function getStoredToken(): string | null {
    return sessionStorage.getItem(SESSION_TOKEN_KEY) ?? localStorage.getItem(SESSION_TOKEN_KEY);
}

export function clearStoredToken(): void {
    sessionStorage.removeItem(SESSION_TOKEN_KEY);
    localStorage.removeItem(SESSION_TOKEN_KEY);
}

export function getAuthorizationHeader(): Record<string, string> {
    const token = getStoredToken();
    if (!token) throw new Error("Your sign-in session is no longer valid.");
    return { Authorization: `Bearer ${token}` };
}

export function storeToken(token: string, remember: boolean): void {
    clearStoredToken();
    (remember ? localStorage : sessionStorage).setItem(SESSION_TOKEN_KEY, token);
}

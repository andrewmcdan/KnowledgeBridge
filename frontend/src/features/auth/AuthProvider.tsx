import { useEffect, useMemo, useState, type ReactNode } from "react";
import { authContext, type AuthContextValue, type AuthStatus } from "./AuthContext";
import { getCurrentUser, login, type AuthUser } from "./authApi";

const SESSION_TOKEN_KEY = "knowledgebridge.auth.token";

function getStoredToken(): string | null {
    return sessionStorage.getItem(SESSION_TOKEN_KEY) ?? localStorage.getItem(SESSION_TOKEN_KEY);
}

function clearStoredToken() {
    sessionStorage.removeItem(SESSION_TOKEN_KEY);
    localStorage.removeItem(SESSION_TOKEN_KEY);
}

export function AuthProvider({ children }: { children: ReactNode }) {
    const [status, setStatus] = useState<AuthStatus>(() => (getStoredToken() ? "loading" : "unauthenticated"));
    const [user, setUser] = useState<AuthUser | null>(null);

    useEffect(() => {
        const token = getStoredToken();
        if (!token) return;

        let cancelled = false;
        getCurrentUser(token)
            .then((currentUser) => {
                if (cancelled) return;
                setUser(currentUser);
                setStatus("authenticated");
            })
            .catch(() => {
                if (cancelled) return;
                clearStoredToken();
                setStatus("unauthenticated");
            });

        return () => {
            cancelled = true;
        };
    }, []);

    const value = useMemo<AuthContextValue>(
        () => ({
            status,
            user,
            async signIn(email, password, remember) {
                const response = await login(email, password);
                clearStoredToken();
                (remember ? localStorage : sessionStorage).setItem(SESSION_TOKEN_KEY, response.token);
                setUser(response.user);
                setStatus("authenticated");
            },
            signOut() {
                clearStoredToken();
                setUser(null);
                setStatus("unauthenticated");
            },
        }),
        [status, user],
    );

    return <authContext.Provider value={value}>{children}</authContext.Provider>;
}

import { useEffect, useMemo, useState, type ReactNode } from "react";
import { authContext, type AuthContextValue, type AuthStatus } from "./AuthContext";
import { getCurrentUser, login, logout, register, type AuthUser, type LoginResponse } from "./authApi";
import { AUTH_EXPIRED_EVENT } from "../../api/apiError";

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
        const expireSession = (event: Event) => {
            // Ignore a delayed response from a token that has already been replaced by a new login.
            if ((event as CustomEvent<string>).detail !== `Bearer ${getStoredToken()}`) return;
            clearStoredToken();
            setUser(null);
            setStatus("unauthenticated");
        };
        window.addEventListener(AUTH_EXPIRED_EVENT, expireSession);
        return () => window.removeEventListener(AUTH_EXPIRED_EVENT, expireSession);
    }, []);

    useEffect(() => {
        const token = getStoredToken();
        if (!token) return;

        let cancelled = false;
        getCurrentUser(token)
            .then((currentUser) => {
                if (cancelled || getStoredToken() !== token) return;
                setUser(currentUser);
                setStatus("authenticated");
            })
            .catch(() => {
                if (cancelled || getStoredToken() !== token) return;
                clearStoredToken();
                setStatus("unauthenticated");
            });

        return () => {
            cancelled = true;
        };
    }, []);

    const value = useMemo<AuthContextValue>(() => {
        const acceptSession = (response: LoginResponse, remember: boolean) => {
            clearStoredToken();
            (remember ? localStorage : sessionStorage).setItem(SESSION_TOKEN_KEY, response.token);
            setUser(response.user);
            setStatus("authenticated");
        };
        return {
            status,
            user,
            async signIn(email, password, remember) {
                acceptSession(await login(email, password), remember);
            },
            async signUp(displayName, email, password) {
                acceptSession(await register(displayName, email, password), false);
            },
            async signOut() {
                const token = getStoredToken();
                clearStoredToken();
                setUser(null);
                setStatus("unauthenticated");
                // Local sign-out must complete even when the stateless API is unreachable.
                if (token) await logout(token).catch(() => undefined);
            },
        };
    }, [status, user]);

    return <authContext.Provider value={value}>{children}</authContext.Provider>;
}

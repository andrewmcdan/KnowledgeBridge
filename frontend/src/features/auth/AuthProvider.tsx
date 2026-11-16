import { useEffect, useMemo, useState, type ReactNode } from "react";
import { authContext, type AuthContextValue, type AuthStatus } from "./AuthContext";
import { getCurrentUser, login, logout, register, type AuthUser, type LoginResponse } from "./authApi";
import { AUTH_EXPIRED_EVENT } from "../../api/apiError";
import { clearStoredToken, getStoredToken, storeToken } from "./authSession";

export function AuthProvider({ children }: { children: ReactNode }) {
    const [status, setStatus] = useState<AuthStatus>(() => (getStoredToken() ? "loading" : "unauthenticated"));
    const [user, setUser] = useState<AuthUser | null>(null);
    const [sessionExpired, setSessionExpired] = useState(false);

    const endSession = (expired: boolean) => {
        clearStoredToken();
        setUser(null);
        setStatus("unauthenticated");
        setSessionExpired(expired);
    };

    useEffect(() => {
        const expireSession = (event: Event) => {
            // Ignore a delayed response from a token that has already been replaced by a new login.
            if ((event as CustomEvent<string>).detail !== `Bearer ${getStoredToken()}`) return;
            endSession(true);
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
                endSession(true);
            });

        return () => {
            cancelled = true;
        };
    }, []);

    const value = useMemo<AuthContextValue>(() => {
        const acceptSession = (response: LoginResponse, remember: boolean) => {
            storeToken(response.token, remember);
            setSessionExpired(false);
            setUser(response.user);
            setStatus("authenticated");
        };
        return {
            status,
            user,
            sessionExpired,
            async signIn(email, password, remember) {
                acceptSession(await login(email, password), remember);
            },
            async signUp(displayName, email, password) {
                acceptSession(await register(displayName, email, password), false);
            },
            async signOut() {
                const token = getStoredToken();
                endSession(false);
                // Local sign-out must complete even when the stateless API is unreachable.
                if (token) await logout(token).catch(() => undefined);
            },
        };
    }, [status, user, sessionExpired]);

    return <authContext.Provider value={value}>{children}</authContext.Provider>;
}

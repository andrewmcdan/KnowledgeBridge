import { createContext } from "react";
import type { AuthUser } from "./authApi";

export type AuthStatus = "loading" | "unauthenticated" | "authenticated";

export interface AuthContextValue {
    status: AuthStatus;
    user: AuthUser | null;
    signIn: (email: string, password: string, remember: boolean) => Promise<void>;
    signOut: () => Promise<void>;
}

export const authContext = createContext<AuthContextValue | null>(null);

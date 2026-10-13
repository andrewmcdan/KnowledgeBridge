import { useContext } from "react";
import { authContext, type AuthContextValue } from "./AuthContext";

export function useAuth(): AuthContextValue {
    const value = useContext(authContext);
    if (value === null) throw new Error("useAuth must be used within an AuthProvider.");
    return value;
}

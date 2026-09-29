import { useEffect } from "react";

export function useGlobalShortcuts(closeMobileNavigation: () => void) {
    useEffect(() => {
        const onKey = (event: KeyboardEvent) => {
            if ((event.ctrlKey || event.metaKey) && event.key === "k") {
                event.preventDefault();
                document.getElementById("global-search")?.focus();
            }
            if (event.key === "Escape") closeMobileNavigation();
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [closeMobileNavigation]);
}

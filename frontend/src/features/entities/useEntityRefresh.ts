import { useEffect } from "react";

export const ENTITIES_UPDATED_EVENT = "knowledgebridge:entities-updated";
const REFRESH_INTERVAL_MS = 10_000;

export function useEntityRefresh(load: (signal: AbortSignal, initial: boolean) => Promise<void>, initialDelay = 0) {
    useEffect(() => {
        const controller = new AbortController();
        let timer: number;
        let running = false;
        let initial = true;

        const refresh = async () => {
            if (controller.signal.aborted || running) return;
            window.clearTimeout(timer);
            running = true;
            try {
                if (initial || document.visibilityState === "visible") {
                    await load(controller.signal, initial);
                    initial = false;
                }
            } finally {
                running = false;
                if (!controller.signal.aborted) timer = window.setTimeout(() => void refresh(), REFRESH_INTERVAL_MS);
            }
        };
        const refreshVisible = () => {
            if (document.visibilityState === "visible") void refresh();
        };

        timer = window.setTimeout(() => void refresh(), initialDelay);
        window.addEventListener("focus", refreshVisible);
        window.addEventListener(ENTITIES_UPDATED_EVENT, refreshVisible);
        document.addEventListener("visibilitychange", refreshVisible);
        return () => {
            controller.abort();
            window.clearTimeout(timer);
            window.removeEventListener("focus", refreshVisible);
            window.removeEventListener(ENTITIES_UPDATED_EVENT, refreshVisible);
            document.removeEventListener("visibilitychange", refreshVisible);
        };
    }, [load, initialDelay]);
}

import { useEffect, useState } from "react";
import { fetchHealthStatus } from "../api/healthApi";

export function useHealthStatus() {
    const [service, setService] = useState("Checking connection…");

    useEffect(() => {
        const controller = new AbortController();
        void fetchHealthStatus(controller.signal)
            .then(setService)
            .catch(() => {
                if (!controller.signal.aborted) setService("Backend unavailable");
            });
        return () => controller.abort();
    }, []);

    return service;
}

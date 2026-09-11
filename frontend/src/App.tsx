import { useCallback, useEffect, useState } from "react";
import "./App.css";
import wordmark from "./assets/wordmark.png";

type ServiceStatus = "UP" | "DOWN";

interface HealthResponse {
    status: ServiceStatus;
    database: ServiceStatus;
    timestamp: string;
}

type HealthState = { phase: "loading" } | { phase: "ready"; data: HealthResponse } | { phase: "error"; message: string };

async function fetchHealth(): Promise<HealthResponse> {
    const response = await fetch("/api/health");
    if (!response.ok) throw new Error(`Health request returned ${response.status}`);
    return (await response.json()) as HealthResponse;
}

function App() {
    const [health, setHealth] = useState<HealthState>({ phase: "loading" });

    const loadHealth = useCallback(async () => {
        setHealth({ phase: "loading" });
        try {
            setHealth({ phase: "ready", data: await fetchHealth() });
        } catch (error) {
            setHealth({
                phase: "error",
                message: error instanceof Error ? error.message : "Health request failed",
            });
        }
    }, []);

    useEffect(() => {
        let active = true;

        void fetchHealth()
            .then((data) => {
                if (active) setHealth({ phase: "ready", data });
            })
            .catch((error: unknown) => {
                if (active) {
                    setHealth({
                        phase: "error",
                        message: error instanceof Error ? error.message : "Health request failed",
                    });
                }
            });

        return () => {
            active = false;
        };
    }, []);

    const isHealthy = health.phase === "ready" && health.data.status === "UP";

    return (
        <main className="app-shell">
            <header className="brand-bar">
                <a className="brand" href="/" aria-label="KnowledgeBridge home">
                    <img className="brand-wordmark" src={wordmark} alt="KnowledgeBridge" width="1164" height="204" />
                </a>
                <span className="environment">Development environment</span>
            </header>

            <section className="hero" aria-labelledby="page-title">
                <p className="eyebrow">Milestone 1 · Platform foundation</p>
                <h1 id="page-title">Business knowledge, connected.</h1>
                <p className="lede">The first KnowledgeBridge service slice is online. This page confirms that the React interface can reach the Spring Boot API and its PostgreSQL knowledge store.</p>
            </section>

            <section className="status-panel" aria-labelledby="status-title">
                <div className="status-heading">
                    <div>
                        <p className="eyebrow">System status</p>
                        <h2 id="status-title">Development services</h2>
                    </div>
                    <button type="button" onClick={() => void loadHealth()} disabled={health.phase === "loading"}>
                        {health.phase === "loading" ? "Checking…" : "Check again"}
                    </button>
                </div>

                {health.phase === "loading" && (
                    <div className="status-message" role="status">
                        Contacting the backend…
                    </div>
                )}
                {health.phase === "error" && (
                    <div className="status-message error" role="alert">
                        <strong>Unable to reach the platform.</strong>
                        <span>{health.message}</span>
                    </div>
                )}
                {health.phase === "ready" && (
                    <div className="service-grid" aria-live="polite">
                        <article className="service-card">
                            <div className="service-title">
                                <span className={`indicator ${health.data.status.toLowerCase()}`} aria-hidden="true" />
                                <h3>Spring Boot API</h3>
                            </div>
                            <p>REST boundary for authentication, ingestion, queries, and administration.</p>
                            <span className={`badge ${health.data.status.toLowerCase()}`}>{health.data.status}</span>
                        </article>
                        <article className="service-card">
                            <div className="service-title">
                                <span className={`indicator ${health.data.database.toLowerCase()}`} aria-hidden="true" />
                                <h3>PostgreSQL + pgvector</h3>
                            </div>
                            <p>Application metadata, cache records, usage events, and vector-ready storage.</p>
                            <span className={`badge ${health.data.database.toLowerCase()}`}>{health.data.database}</span>
                        </article>
                    </div>
                )}

                <footer className="status-footer">
                    <span className={`summary-dot ${isHealthy ? "up" : ""}`} aria-hidden="true" />
                    {health.phase === "ready" ? `Last checked ${new Date(health.data.timestamp).toLocaleString()}` : "Waiting for a successful health check"}
                </footer>
            </section>
        </main>
    );
}

export default App;

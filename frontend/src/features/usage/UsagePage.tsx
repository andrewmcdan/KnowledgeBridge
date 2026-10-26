import { useEffect, useState } from "react";
import { getUsageTotals, type UsageTotals } from "./usageApi";

export function UsageTotalsPanel({ refreshKey = 0 }: { refreshKey?: number }) {
    const [snapshot, setSnapshot] = useState<{ key: string; totals: UsageTotals | null; error: string | null } | null>(null);
    const [reload, setReload] = useState(0);
    const requestKey = `${refreshKey}-${reload}`;
    const totals = snapshot?.totals ?? null;
    const error = snapshot?.key === requestKey ? snapshot.error : null;
    const loading = snapshot?.key !== requestKey;
    useEffect(() => {
        let active = true;
        void getUsageTotals()
            .then((value) => {
                if (active) setSnapshot({ key: requestKey, totals: value, error: null });
            })
            .catch((reason: unknown) => {
                if (active) setSnapshot((previous) => ({ key: requestKey, totals: previous?.totals ?? null, error: reason instanceof Error ? reason.message : "We could not load usage totals." }));
            });
        return () => {
            active = false;
        };
    }, [requestKey]);

    return (
        <section className="collection panel usage-panel" aria-label="Token usage totals">
            <div className="collection-heading">
                <div>
                    <h2>{totals?.scope === "workspace" ? "Workspace usage" : "Your usage"}</h2>
                    <p>Persisted totals for successful queries since usage tracking was enabled.</p>
                </div>
                <button className="secondary-button" type="button" disabled={loading} onClick={() => setReload((value) => value + 1)}>
                    {loading ? "Refreshing usage…" : "Refresh usage"}
                </button>
            </div>
            {error && (
                <p className="upload-error" role="alert">
                    {error}
                </p>
            )}
            {totals && (
                <dl className="usage-totals" aria-live="polite">
                    <div>
                        <dt>Total synthesis tokens</dt>
                        <dd>{totals.totalTokens.toLocaleString()}</dd>
                    </div>
                    <div>
                        <dt>Input tokens</dt>
                        <dd>{totals.inputTokens.toLocaleString()}</dd>
                    </div>
                    <div>
                        <dt>Output tokens</dt>
                        <dd>{totals.outputTokens.toLocaleString()}</dd>
                    </div>
                    <div>
                        <dt>Successful queries</dt>
                        <dd>{totals.queryCount.toLocaleString()}</dd>
                    </div>
                    <div>
                        <dt>Cache hits</dt>
                        <dd>{totals.cacheHits.toLocaleString()}</dd>
                    </div>
                    <div>
                        <dt>Queries with estimates</dt>
                        <dd>{totals.estimatedQueryCount.toLocaleString()}</dd>
                    </div>
                </dl>
            )}
            <p className="usage-explanation">
                Counts use engine-reported tokens when available. Otherwise, estimates use four characters per token: the question plus full cited note/document text for input, and the answer for output. Actual prompts and retrieved
                excerpts can differ.
            </p>
            <p className="usage-explanation">
                Cache hits and search-only queries add zero synthesis tokens. Totals exclude embedding, ingestion, failed or unreported provider calls, and activity before tracking began. These are usage observations, not a billing
                statement.
            </p>
        </section>
    );
}

export function UsagePage() {
    return <UsageTotalsPanel />;
}

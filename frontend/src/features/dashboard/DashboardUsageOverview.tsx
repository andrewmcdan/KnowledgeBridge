import { useEffect, useMemo, useState } from "react";
import { getEntities, type DetectedEntity } from "../entities/entitiesApi";
import { getKnowledgeItems, type KnowledgeItem } from "../knowledge/knowledgeApi";
import { getUsageTotals, type UsageTotals } from "../usage/usageApi";

interface DashboardData {
    totals: UsageTotals;
    items: KnowledgeItem[];
    entities: DetectedEntity[];
}

function demoItem(id: number, title: string, sourceKind: KnowledgeItem["sourceKind"], updatedAt: string): KnowledgeItem {
    return {
        id,
        title,
        type: "Document",
        ownerId: 1,
        status: "COMPLETED",
        ingestionStage: "COMPLETED",
        characterCount: 0,
        externalEngineId: `demo-${id}`,
        lastErrorCode: null,
        lastErrorMessage: null,
        createdAt: updatedAt,
        updatedAt,
        sourceKind,
    };
}

const DEMO_DATA: DashboardData = {
    totals: {
        scope: "workspace",
        queryCount: 428751,
        inputTokens: 827000000,
        outputTokens: 413000000,
        totalTokens: 1240000000,
        cacheHits: 327189,
        estimatedQueryCount: 0,
    },
    items: [
        demoItem(1, "Q4 Product Strategy", "MARKDOWN", "2026-11-02T15:40:00Z"),
        demoItem(2, "Engineering Onboarding Guide", "PDF", "2026-11-02T14:25:00Z"),
        demoItem(3, "Customer Success Playbook", "DOCX", "2026-11-02T12:15:00Z"),
        demoItem(4, "Platform Walkthrough", "VIDEO", "2026-11-02T10:50:00Z"),
        demoItem(5, "Security Review Notes", "MANUAL", "2026-11-02T09:30:00Z"),
        demoItem(6, "API Design Standards", "MARKDOWN", "2026-11-01T16:20:00Z"),
        demoItem(7, "Incident Response Guide", "PDF", "2026-11-01T15:10:00Z"),
        demoItem(8, "Feature Demo Recording", "VIDEO", "2026-11-01T13:45:00Z"),
        demoItem(9, "Data Retention Policy", "DOCX", "2026-11-01T11:15:00Z"),
        demoItem(10, "Release Planning Notes", "MANUAL", "2026-11-01T09:00:00Z"),
    ],
    entities: Array.from({ length: 124 }, (_, index) => ({
        id: index + 1,
        name: `Demo entity ${index + 1}`,
        type: (["PERSON", "COMPANY", "PROJECT", "TEAM"] as const)[index % 4],
        summary: "",
        documentCount: 1,
        verified: index % 3 !== 0,
    })),
};

function percentage(value: number, total: number): number {
    return total === 0 ? 0 : Math.round((value / total) * 100);
}

export function DashboardUsageOverview() {
    const [data, setData] = useState<DashboardData | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [reload, setReload] = useState(0);
    const [demoMode, setDemoMode] = useState(false);

    useEffect(() => {
        let active = true;
        void Promise.all([getUsageTotals(), getKnowledgeItems(), getEntities("", "", 100)])
            .then(([totals, items, entities]) => {
                if (!active) return;
                setData({ totals, items, entities });
                setError(null);
            })
            .catch((reason: unknown) => {
                if (active) setError(reason instanceof Error ? reason.message : "We could not load usage analytics.");
            });
        return () => {
            active = false;
        };
    }, [reload]);

    const metrics = useMemo(() => {
        const activeData = demoMode ? DEMO_DATA : data;
        if (!activeData) return null;
        return {
            cacheHitRate: percentage(activeData.totals.cacheHits, activeData.totals.queryCount),
        };
    }, [data, demoMode]);
    const activeData = demoMode ? DEMO_DATA : data;

    return (
        <section className="dashboard-analytics" aria-label="Usage and performance analytics" aria-busy={!activeData && !error}>
            <header className="dashboard-analytics-heading">
                <div>
                    <span className="graph-eyebrow">Analytics</span>
                    <h2>AI Analytics Dashboard</h2>
                    <p>Usage, performance, and knowledge insights for your workspace.</p>
                </div>
                <div className="dashboard-analytics-actions">
                    <label className="dashboard-demo-switch">
                        <input type="checkbox" checked={demoMode} onChange={(event) => setDemoMode(event.target.checked)} />
                        <span aria-hidden="true" />
                        Demo mode
                    </label>
                    <button className="secondary-button" type="button" disabled={!activeData && !error} onClick={() => setReload((value) => value + 1)}>
                        {activeData ? "Refresh analytics" : error ? "Try again" : "Loading analytics…"}
                    </button>
                </div>
            </header>
            {demoMode && <p className="dashboard-demo-notice">Demo mode is showing presentation data, not workspace data.</p>}
            {error && !demoMode && (
                <p className="dashboard-analytics-error" role="alert">
                    {error}
                </p>
            )}
            {activeData && metrics && (
                <>
                    <dl className="dashboard-metric-cards dashboard-metric-cards-three">
                        <div>
                            <dt>Successful queries</dt>
                            <dd>{activeData.totals.queryCount.toLocaleString()}</dd>
                            <span>{activeData.totals.scope === "workspace" ? "Workspace total" : "Your total"} since tracking began</span>
                        </div>
                        <div>
                            <dt>Total tokens used</dt>
                            <dd>{activeData.totals.totalTokens.toLocaleString()}</dd>
                            <span>
                                {activeData.totals.inputTokens.toLocaleString()} input · {activeData.totals.outputTokens.toLocaleString()} output
                            </span>
                        </div>
                        <div>
                            <dt>Cache hit rate</dt>
                            <dd>{metrics.cacheHitRate}%</dd>
                            <span>{activeData.totals.cacheHits.toLocaleString()} cached responses</span>
                        </div>
                    </dl>
                    <div className="dashboard-showcase-grid">
                        <section className="dashboard-wide-panel" aria-label="Input versus output token history">
                            <h3>Input vs output tokens</h3>
                            <p>Daily token usage for AI requests</p>
                            {demoMode ? <DemoLineChart /> : <EmptyChart message="Daily input and output token history is not currently recorded." />}
                        </section>
                        <section className="dashboard-side-panel" aria-label="Usage by model">
                            <h3>Usage by Model</h3>
                            {demoMode ? <DemoModelUsage /> : <EmptyChart message="Provider model usage is not currently recorded." variant="donut" />}
                        </section>
                    </div>
                    <div className="dashboard-summary-grid">
                        <section className="dashboard-cache-summary" aria-label="Cached response hits">
                            <h3>Cached Response Hits</h3>
                            <div>
                                <dl>
                                    <div>
                                        <dt>Total cache hits</dt>
                                        <dd>{activeData.totals.cacheHits.toLocaleString()}</dd>
                                    </div>
                                    <div>
                                        <dt>Hit rate</dt>
                                        <dd>{metrics.cacheHitRate}%</dd>
                                    </div>
                                </dl>
                                {demoMode ? <DemoSparkline /> : <EmptyChart message="Cache-hit trend data is not currently recorded." compact />}
                            </div>
                        </section>
                        <section className="dashboard-cost-summary" aria-label="Token spend and cost estimate">
                            <h3>Token Spend & Cost Estimate</h3>
                            {demoMode ? <DemoCostSummary /> : <EmptyChart message="Cost and spend data is not currently recorded." compact />}
                        </section>
                    </div>
                    <div className="dashboard-empty-panels dashboard-knowledge-panels">
                        <DocumentTypesPanel items={activeData.items} />
                        <RecentIngestionPanel items={activeData.items} />
                        <KnownEntitiesPanel entities={activeData.entities} />
                    </div>
                </>
            )}
        </section>
    );
}

function DemoLineChart() {
    return (
        <div className="dashboard-demo-line-chart" role="img" aria-label="Demo daily input and output token usage trend">
            <svg viewBox="0 0 640 210" preserveAspectRatio="none" aria-hidden="true">
                <path d="M0 158 C45 145 66 137 105 145 S155 118 196 126 S248 98 286 113 S338 72 377 88 S429 59 469 78 S523 45 558 60 S608 30 640 42" className="dashboard-demo-input-line" />
                <path d="M0 181 C42 172 72 174 106 164 S157 153 197 160 S249 142 288 151 S340 127 379 138 S430 119 470 129 S522 105 558 117 S609 98 640 105" className="dashboard-demo-output-line" />
            </svg>
            <div className="dashboard-demo-chart-key">
                <span>
                    <i className="dashboard-input-key" /> Input tokens
                </span>
                <span>
                    <i className="dashboard-output-key" /> Output tokens
                </span>
            </div>
        </div>
    );
}

function DemoModelUsage() {
    const models = [
        ["GPT-4.1", "41.2%", "176,856", "#2867eb"],
        ["Claude 3.7", "22.8%", "97,787", "#74b6ff"],
        ["Gemini 1.5", "15.6%", "66,842", "#9f89f3"],
        ["Llama 3.3", "9.1%", "38,991", "#61c98d"],
        ["Internal RAG Agent", "11.3%", "48,275", "#c5cedd"],
    ];
    return (
        <div className="dashboard-demo-models">
            <div className="dashboard-demo-model-donut" role="img" aria-label="Demo usage by model" />
            <ul>
                {models.map(([model, share, requests, color]) => (
                    <li key={model}>
                        <i style={{ background: color }} />
                        <span>{model}</span>
                        <strong>{share}</strong>
                        <small>{requests}</small>
                    </li>
                ))}
            </ul>
        </div>
    );
}

function DemoSparkline() {
    return (
        <div className="dashboard-demo-sparkline" role="img" aria-label="Demo cached response trend">
            <svg viewBox="0 0 260 70" preserveAspectRatio="none" aria-hidden="true">
                <path d="M0 63 L0 57 C20 49 30 53 47 42 S75 47 94 32 S121 40 141 24 S168 31 189 16 S219 21 238 7 S252 10 260 3 V70 H0Z" />
            </svg>
        </div>
    );
}

function DemoCostSummary() {
    return (
        <div className="dashboard-demo-cost">
            <div>
                <strong>$372.40</strong>
                <span>Estimated cost</span>
            </div>
            <div>
                <strong>1.24B</strong>
                <span>Total tokens used</span>
            </div>
        </div>
    );
}

function EmptyChart({ message, variant = "line", compact = false }: { message: string; variant?: "line" | "donut"; compact?: boolean }) {
    return (
        <div className={`dashboard-empty-chart dashboard-empty-chart-${variant} ${compact ? "dashboard-empty-chart-compact" : ""}`}>
            {variant === "donut" && <div className="dashboard-empty-donut" aria-hidden="true" />}
            <div>
                <strong>No current data</strong>
                <span>{message}</span>
            </div>
        </div>
    );
}

function DocumentTypesPanel({ items }: { items: KnowledgeItem[] }) {
    const sourceKinds: Array<[KnowledgeItem["sourceKind"], string]> = [
        ["VIDEO", "MP4"],
        ["MARKDOWN", "Markdown"],
        ["DOCX", "DOCX"],
        ["PDF", "PDF"],
        ["MANUAL", "Manual notes"],
    ];
    const ingested = items.filter((item) => item.status === "COMPLETED");
    const total = ingested.length;
    return (
        <section className="dashboard-empty-panel dashboard-document-types">
            <h3>Document Types</h3>
            <ul>
                {sourceKinds.map(([kind, label]) => {
                    const count = ingested.filter((item) => item.sourceKind === kind).length;
                    const share = total === 0 ? 0 : Math.round((count / total) * 100);
                    return (
                        <li key={kind}>
                            <div>
                                <span>{label}</span>
                                <strong>{share}%</strong>
                            </div>
                            <div className="dashboard-document-type-bar" role="img" aria-label={`${label}: ${share}% of completed ingestions`}>
                                <i style={{ width: `${share}%` }} />
                            </div>
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}

function RecentIngestionPanel({ items }: { items: KnowledgeItem[] }) {
    const recent = items
        .filter((item) => item.status === "COMPLETED")
        .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
        .slice(0, 5);
    return (
        <section className="dashboard-empty-panel dashboard-recent-ingestion">
            <h3>Upload / Ingestion Status</h3>
            {recent.length ? (
                <ul>
                    {recent.map((item) => (
                        <li key={item.id}>
                            <span>{item.title}</span>
                            <small>{item.status.toLowerCase()}</small>
                        </li>
                    ))}
                </ul>
            ) : (
                <EmptyChart message="No completed knowledge items are available yet." compact />
            )}
        </section>
    );
}

function KnownEntitiesPanel({ entities }: { entities: DetectedEntity[] }) {
    const byType = new Map(entities.map((entity) => [entity.type, 0]));
    const entityTypes = ["PERSON", "COMPANY", "PROJECT", "TEAM"] as const;
    for (const entity of entities) byType.set(entity.type, (byType.get(entity.type) ?? 0) + 1);
    return (
        <section className="dashboard-empty-panel dashboard-known-entities">
            <h3>Known Entities</h3>
            <strong>{entities.length === 100 ? "100+" : entities.length.toLocaleString()}</strong>
            <span>entities currently available</span>
            <ul>
                {entityTypes.map((type) => (
                    <li key={type}>
                        <span>
                            {type[0]}
                            {type.slice(1).toLowerCase()}
                        </span>
                        <small>{(byType.get(type) ?? 0).toLocaleString()}</small>
                    </li>
                ))}
            </ul>
        </section>
    );
}

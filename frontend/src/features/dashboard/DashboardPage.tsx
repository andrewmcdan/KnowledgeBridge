import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import type { Detail } from "../../types/models";
import { ENTITIES_UPDATED_EVENT } from "../entities/useEntityRefresh";
import { DashboardUsageOverview } from "./DashboardUsageOverview";
import { loadEntityGraph, type GraphSnapshot } from "./graphApi";
import { ENTITY_COLORS, ENTITY_LABELS, filterEntityGraph, graphDocumentCount, type GraphSelection } from "./graphData";
import { GraphDetails } from "./GraphDetails";
import { GraphList } from "./GraphList";
import { GraphRenderBoundary } from "./GraphRenderBoundary";
import { GraphViewSettings } from "./GraphViewSettings";
import { DEFAULT_GRAPH_VIEW, type GraphViewOptions } from "./graphViewOptions";
import "./dashboard.css";

const EntityGraphCanvas = lazy(() => import("./EntityGraphCanvas"));

export function DashboardPage({ onOpenDetail }: { onOpenDetail: (detail: Detail) => void }) {
    const [response, setResponse] = useState<{ key: string; snapshot: GraphSnapshot | null; error: string | null } | null>(null);
    const [reload, setReload] = useState(0);
    const [limit, setLimit] = useState(50);
    const [query, setQuery] = useState("");
    const [type, setType] = useState("");
    const [minimumDocuments, setMinimumDocuments] = useState(1);
    const [view, setView] = useState<"graph" | "list">("graph");
    const [selection, setSelection] = useState<GraphSelection>(null);
    const [viewSettings, setViewSettings] = useState<GraphViewOptions>({ ...DEFAULT_GRAPH_VIEW });
    const [layoutPaused, setLayoutPaused] = useState(false);
    const requestKey = `${limit}:${reload}`;
    const loading = response?.key !== requestKey;
    const snapshot = response?.key === requestKey ? response.snapshot : null;
    const error = response?.key === requestKey ? response.error : null;

    useEffect(() => {
        const controller = new AbortController();
        loadEntityGraph(limit, controller.signal)
            .then((result) => {
                if (!controller.signal.aborted) setResponse({ key: requestKey, snapshot: result, error: null });
            })
            .catch((reason: unknown) => {
                if (!controller.signal.aborted) setResponse({ key: requestKey, snapshot: null, error: reason instanceof Error ? reason.message : "The knowledge graph could not be loaded." });
            });
        return () => controller.abort();
    }, [limit, requestKey]);

    useEffect(() => {
        const refresh = () => setReload((value) => value + 1);
        window.addEventListener(ENTITIES_UPDATED_EVENT, refresh);
        return () => window.removeEventListener(ENTITIES_UPDATED_EVENT, refresh);
    }, []);

    const graph = useMemo(() => filterEntityGraph(snapshot?.graph ?? { nodes: [], links: [] }, query, type, minimumDocuments), [snapshot, query, type, minimumDocuments]);
    const documentCount = useMemo(() => graphDocumentCount(graph), [graph]);
    const filtered = query.trim() !== "" || type !== "" || minimumDocuments !== 1;

    return (
        <>
            <DashboardUsageOverview />
            <section className="knowledge-map panel" aria-label="Knowledge relationship dashboard" aria-busy={loading}>
                <header className="knowledge-map-heading">
                    <div>
                        <span className="graph-eyebrow">Workspace intelligence</span>
                        <h2>
                            Knowledge map <span className="graph-dimension-badge">3D</span>
                        </h2>
                        <p>Discover the documents connecting your people, companies, projects, and teams.</p>
                    </div>
                    <button className="secondary-button" type="button" disabled={loading} onClick={() => setReload((value) => value + 1)}>
                        {loading ? "Loading graph…" : "Refresh graph"}
                    </button>
                </header>
                <div className="graph-metrics" aria-label="Visible graph totals">
                    <div>
                        <strong>{graph.nodes.length}</strong>
                        <span>Entities</span>
                    </div>
                    <div>
                        <strong>{graph.links.length}</strong>
                        <span>Connections</span>
                    </div>
                    <div>
                        <strong>{documentCount}</strong>
                        <span>Source documents</span>
                    </div>
                    <p>
                        One link per entity pair.
                        <br />
                        Multiple shared documents can support a link.
                    </p>
                </div>
                <div className="graph-filter-bar">
                    <label htmlFor="graph-filter-name">
                        Filter entities
                        <input id="graph-filter-name" type="search" maxLength={255} placeholder="Name and connections…" value={query} onChange={(event) => setQuery(event.target.value)} />
                    </label>
                    <label htmlFor="graph-filter-type">
                        Entity type
                        <select id="graph-filter-type" value={type} onChange={(event) => setType(event.target.value)}>
                            <option value="">All types</option>
                            {Object.entries(ENTITY_LABELS).map(([value, label]) => (
                                <option key={value} value={value}>
                                    {label}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label htmlFor="graph-filter-documents">
                        Shared documents
                        <select id="graph-filter-documents" value={minimumDocuments} onChange={(event) => setMinimumDocuments(Number(event.target.value))}>
                            <option value={1}>1 or more</option>
                            <option value={2}>2 or more</option>
                            <option value={3}>3 or more</option>
                            <option value={5}>5 or more</option>
                        </select>
                    </label>
                    <label htmlFor="graph-filter-limit">
                        Entity limit
                        <select id="graph-filter-limit" value={limit} disabled={loading} onChange={(event) => setLimit(Number(event.target.value))}>
                            <option value={25}>25 entities</option>
                            <option value={50}>50 entities</option>
                            <option value={100}>100 entities</option>
                        </select>
                    </label>
                    {filtered && (
                        <button
                            className="text-button"
                            type="button"
                            onClick={() => {
                                setQuery("");
                                setType("");
                                setMinimumDocuments(1);
                            }}
                        >
                            Clear filters
                        </button>
                    )}
                </div>
                <div className="graph-view-heading">
                    <ul className="graph-legend" aria-label="Entity color legend">
                        {Object.entries(ENTITY_LABELS).map(([value, label]) => (
                            <li key={value}>
                                <i style={{ background: ENTITY_COLORS[value as keyof typeof ENTITY_COLORS] }} />
                                {label}
                            </li>
                        ))}
                    </ul>
                    <div className="graph-view-switch" role="group" aria-label="Graph presentation">
                        <button type="button" aria-pressed={view === "graph"} onClick={() => setView("graph")}>
                            3D graph
                        </button>
                        <button type="button" aria-pressed={view === "list"} onClick={() => setView("list")}>
                            List view
                        </button>
                    </div>
                </div>
                <GraphViewSettings
                    settings={viewSettings}
                    onChange={(settings) => {
                        if (settings.spacing !== viewSettings.spacing) setLayoutPaused(false);
                        setViewSettings(settings);
                    }}
                />
                {error && (
                    <div className="graph-feedback query-error" role="alert">
                        <p>{error}</p>
                        <button className="secondary-button" type="button" onClick={() => setReload((value) => value + 1)}>
                            Try loading graph again
                        </button>
                    </div>
                )}
                {loading && (
                    <div className="graph-loading" role="status">
                        Gathering entities and their source documents…
                    </div>
                )}
                {!loading && !error && !snapshot?.graph.nodes.length && (
                    <div className="graph-empty">
                        <h3>Your knowledge map starts with a document</h3>
                        <p>No extracted entities are available yet. Add and index knowledge, then extract its entities to see connections here.</p>
                    </div>
                )}
                {!loading && !error && !!snapshot?.graph.nodes.length && (
                    <>
                        {(snapshot.entityLimitReached || snapshot.documentLimitReached) && (
                            <p className="query-warning graph-limit-note">
                                This is a bounded view.{snapshot.entityLimitReached ? ` The first ${limit} entities were returned; increase the entity limit to explore more, up to 100.` : ""}
                                {snapshot.documentLimitReached ? " Some document lists may be incomplete (maximum 100 per entity)." : ""} Connections outside this snapshot are not shown.
                            </p>
                        )}
                        {!graph.nodes.length ? (
                            <div className="graph-empty">
                                <h3>No entities match these filters</h3>
                                <p>Try another name or entity type, or clear the filters.</p>
                            </div>
                        ) : (
                            <div className="graph-workbench">
                                <div className="graph-stage">
                                    {view === "list" ? (
                                        <GraphList graph={graph} selection={selection} onSelect={setSelection} />
                                    ) : (
                                        <GraphRenderBoundary key={reload}>
                                            <Suspense
                                                fallback={
                                                    <div className="graph-loading" role="status">
                                                        Loading 3D renderer…
                                                    </div>
                                                }
                                            >
                                                <EntityGraphCanvas
                                                    graph={graph}
                                                    selection={selection}
                                                    onSelect={setSelection}
                                                    settings={viewSettings}
                                                    onSettingsChange={setViewSettings}
                                                    paused={layoutPaused}
                                                    onPausedChange={setLayoutPaused}
                                                />
                                            </Suspense>
                                        </GraphRenderBoundary>
                                    )}
                                    {!graph.links.length && <p className="graph-no-links">No shared-document connections match these filters. Isolated entities are still shown.</p>}
                                </div>
                                <GraphDetails graph={graph} selection={selection} onSelect={setSelection} onOpenDetail={onOpenDetail} />
                            </div>
                        )}
                    </>
                )}
                <footer className="graph-provenance">
                    Links mean “appears in the same document,” not a verified business relationship. Detections may be noisy. This snapshot loads up to {limit} entities and 100 documents per entity through the application API; refresh after
                    content changes.
                </footer>
            </section>
        </>
    );
}

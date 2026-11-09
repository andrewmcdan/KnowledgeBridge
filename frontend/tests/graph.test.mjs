import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import { runInNewContext } from "node:vm";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";

const packageRequire = createRequire(import.meta.url);
function load(name, overrides = {}, globals = {}) {
    const module = { exports: {} };
    const source = readFileSync(new URL(`../src/features/dashboard/${name}`, import.meta.url), "utf8");
    runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText, {
        module,
        exports: module.exports,
        Map,
        Set,
        ...globals,
        require: (specifier) => {
            if (specifier in overrides) return overrides[specifier];
            if (specifier.endsWith(".css")) return {};
            if (specifier.startsWith("./")) return load(`${specifier.slice(2)}.ts`, overrides);
            return packageRequire(specifier);
        },
    });
    return module.exports;
}
const { buildEntityGraph, filterEntityGraph, graphDocumentCount, escapeGraphLabel } = load("graphData.ts");
const { GraphDetails } = load("GraphDetails.tsx");
const { GraphList } = load("GraphList.tsx");
const { GraphViewSettings } = load("GraphViewSettings.tsx");
const { DEFAULT_GRAPH_VIEW, graphViewValue, graphLayoutForces } = load("graphViewOptions.ts");
const plain = (value) => JSON.parse(JSON.stringify(value));
const entity = (id, name, type = "PERSON") => ({ id, name, type, summary: `${name} summary`, verified: false, documentCount: 2 });
const document = (id, title) => ({ id, title, type: "Policy" });
const budget = document(10, "Budget review");
const policy = document(11, "Procurement policy");
const entries = [
    { entity: entity(3, "Cedar", "PROJECT"), documents: [budget] },
    { entity: entity(1, "Alex"), documents: [policy, budget, budget] },
    { entity: entity(2, "Priya"), documents: [budget, policy] },
    { entity: entity(4, "Isolated", "TEAM"), documents: [document(12, "Onboarding")] },
];

test("shared documents form one undirected edge per entity pair, retaining every unique source", () => {
    const original = plain(entries);
    const graph = buildEntityGraph(entries);
    assert.equal(graph.nodes.length, 4);
    assert.equal(graph.links.length, 3, "three entities in one document form three pairs, not document nodes");
    assert.deepEqual(
        Array.from(graph.links, (link) => link.id),
        ["1:2", "1:3", "2:3"],
    );
    assert.deepEqual(
        Array.from(graph.links[0].documents, (doc) => doc.id),
        [10, 11],
    );
    assert.equal(graphDocumentCount(graph), 3);
    assert.equal(graph.nodes.find((node) => node.id === 1).documents.length, 2);
    assert.ok(
        graph.nodes.some((node) => node.id === 4),
        "isolated entities remain visible",
    );
    assert.ok(
        graph.links.every((link) => link.source < link.target),
        "no self-edges or mirrored duplicates",
    );
    assert.deepEqual(plain(entries), original, "API inputs must not be mutated");
    assert.deepEqual(plain(buildEntityGraph([...entries].reverse())), plain(graph), "network completion order must not change identities");
});

test("duplicate entity records merge sources, equal names keep distinct IDs, and empty data is valid", () => {
    const graph = buildEntityGraph([
        { entity: entity(1, "Same name"), documents: [budget] },
        { entity: entity(1, "Same name"), documents: [policy] },
        { entity: entity(2, "Same name"), documents: [policy] },
    ]);
    assert.equal(graph.nodes.length, 2);
    assert.equal(graph.nodes[0].documents.length, 2);
    assert.equal(graph.links.length, 1);
    assert.deepEqual(
        Array.from(graph.links[0].documents, (doc) => doc.id),
        [11],
    );
    assert.deepEqual(plain(buildEntityGraph([])), { nodes: [], links: [] });
});

test("filters remove dangling edges, retain isolated nodes and never mutate the snapshot", () => {
    const graph = buildEntityGraph(entries);
    const original = plain(graph);
    assert.deepEqual(
        Array.from(filterEntityGraph(graph, "", "", 2).links, (link) => link.id),
        ["1:2"],
    );
    const named = filterEntityGraph(graph, "  ALEX  ", "PERSON", 1);
    assert.deepEqual(
        Array.from(named.nodes, (node) => node.id),
        [1, 2],
    );
    assert.deepEqual(
        Array.from(named.links, (link) => link.id),
        ["1:2"],
    );
    assert.equal(filterEntityGraph(graph, "missing", "", 1).nodes.length, 0);
    assert.equal(filterEntityGraph(graph, "", "PROJECT", 1).nodes.length, 1);
    assert.deepEqual(plain(graph), original);
});

test("name searches include immediate neighbors of every match without expanding through neighbors", () => {
    const shared = document(20, "Alpha and Beta");
    const repeated = document(21, "Alpha and Beta again");
    const distant = document(22, "Beta and Gamma");
    const secondMatch = document(23, "Alphonse and Delta");
    const graph = buildEntityGraph([
        { entity: entity(1, "Alpha"), documents: [shared, repeated] },
        { entity: entity(2, "Beta", "TEAM"), documents: [shared, repeated, distant] },
        { entity: entity(3, "Gamma", "PROJECT"), documents: [distant] },
        { entity: entity(4, "Alphonse", "COMPANY"), documents: [secondMatch] },
        { entity: entity(5, "Delta"), documents: [secondMatch] },
        { entity: entity(6, "Unrelated"), documents: [] },
    ]);
    const original = plain(graph);
    const named = filterEntityGraph(graph, "  ALP  ", "", 1);
    assert.deepEqual(
        Array.from(named.nodes, (node) => node.id),
        [1, 4, 2, 5],
    );
    assert.deepEqual(
        Array.from(named.links, (link) => link.id),
        ["1:2", "4:5"],
    );
    assert.equal(named.links[0].documents.length, 2, "all source documents on a connection remain available");
    assert.deepEqual(
        Array.from(filterEntityGraph(graph, "Gamma", "", 1).nodes, (node) => node.id),
        [2, 3],
        "connections expand from either endpoint",
    );
    assert.deepEqual(
        Array.from(filterEntityGraph(graph, "alp", "PERSON", 1).nodes, (node) => node.id),
        [1],
        "neighbors respect the type filter",
    );
    const stronger = filterEntityGraph(graph, "alp", "", 2);
    assert.deepEqual(
        Array.from(stronger.nodes, (node) => node.id),
        [1, 4, 2],
        "matches remain visible when no connection passes the threshold",
    );
    assert.deepEqual(
        Array.from(stronger.links, (link) => link.id),
        ["1:2"],
    );
    assert.deepEqual(
        Array.from(filterEntityGraph(graph, "Unrelated", "", 1).nodes, (node) => node.id),
        [6],
    );
    assert.deepEqual(plain(filterEntityGraph(graph, "   ", "", 1)), original, "clearing the name restores the complete snapshot");
    assert.deepEqual(plain(graph), original);
});

test("graph tooltip text escapes HTML without changing literal names", () => {
    assert.equal(escapeGraphLabel('<img src=x onerror="bad()"> & O\'Neil'), "&lt;img src=x onerror=&quot;bad()&quot;&gt; &amp; O&#39;Neil");
    assert.equal(escapeGraphLabel("Alex ↔ Priya"), "Alex ↔ Priya");
});

function loader(api) {
    return load("graphApi.ts", { "../entities/entitiesApi": api }).loadEntityGraph;
}

test("graph loading bounds request concurrency, forwards cancellation, and reports snapshot limits", async () => {
    const controller = new AbortController();
    const many = Array.from({ length: 9 }, (_, i) => entity(i + 1, `Entity ${i + 1}`));
    let active = 0;
    let peak = 0;
    const loaded = [];
    const loadGraph = loader({
        getEntities: async (name, type, limit, signal) => {
            assert.deepEqual([name, type, limit, signal], ["", "", 9, controller.signal]);
            return many;
        },
        getEntityDocuments: async (id, signal) => {
            assert.equal(signal, controller.signal);
            peak = Math.max(peak, ++active);
            await new Promise((resolve) => setImmediate(resolve));
            active--;
            loaded.push(id);
            return [budget];
        },
    });
    const result = await loadGraph(9, controller.signal);
    assert.equal(loaded.length, 9);
    assert.equal(peak, 4);
    assert.equal(result.entityLimitReached, true);
    assert.equal(result.documentLimitReached, true, "reported count exceeds returned sources");
    assert.equal(result.graph.links.length, 36);
});

test("a related-document failure rejects the snapshot instead of publishing false empty relationships", async () => {
    const loadGraph = loader({
        getEntities: async () => entries.map((entry) => entry.entity),
        getEntityDocuments: async () => {
            throw new Error("Permission denied");
        },
    });
    await assert.rejects(loadGraph(50, new AbortController().signal), /Permission denied/);
    const failedList = loader({
        getEntities: async () => {
            throw new Error("Expired session");
        },
    });
    await assert.rejects(failedList(50, new AbortController().signal), /Expired session/);
});

test("abandoned loads stop dispatching requests and never return a partial graph", async () => {
    const controller = new AbortController();
    let calls = 0;
    const loadGraph = loader({
        getEntities: async () => Array.from({ length: 10 }, (_, i) => entity(i + 1, `Entity ${i}`)),
        getEntityDocuments: async () => {
            calls++;
            controller.abort();
            return [budget];
        },
    });
    await assert.rejects(loadGraph(50, controller.signal), { name: "AbortError" });
    assert.equal(calls, 1);
    const empty = await loader({ getEntities: async () => [] })(50, new AbortController().signal);
    assert.deepEqual(plain(empty), { graph: { nodes: [], links: [] }, entityLimitReached: false, documentLimitReached: false });
});

function find(node, predicate) {
    if (!node || typeof node !== "object") return null;
    if (Array.isArray(node)) return node.map((child) => find(child, predicate)).find(Boolean) ?? null;
    return predicate(node) ? node : find(node.props?.children, predicate);
}

test("connection selection lists all shared documents and opens the exact full source", () => {
    const graph = buildEntityGraph(entries);
    const opened = [];
    const props = { graph, selection: { kind: "link", id: "1:2" }, onSelect() {}, onOpenDetail: (detail) => opened.push(detail) };
    const html = renderToStaticMarkup(createElement(GraphDetails, props));
    assert.match(html, /Alex/);
    assert.match(html, /Priya/);
    assert.match(html, /Shared documents \(2\)/);
    assert.match(html, /Budget review/);
    assert.match(html, /Procurement policy/);
    const documents = find(GraphDetails(props), (node) => node.type?.name === "GraphDocuments");
    const tree = documents.type(documents.props);
    find(tree, (node) => node.type === "button").props.onClick();
    assert.deepEqual(plain(opened), [{ id: 10, title: "Budget review", text: "" }]);
});

test("entity inspection exposes neighbors, clears selection and ignores selections outside filtered data", () => {
    const graph = buildEntityGraph(entries);
    const selections = [];
    const props = { graph, selection: { kind: "entity", id: 1 }, onSelect: (selection) => selections.push(selection), onOpenDetail() {} };
    const tree = GraphDetails(props);
    find(tree, (node) => node.type === "button" && node.props.className !== "text-button").props.onClick();
    assert.deepEqual(plain(selections[0]), { kind: "link", id: "1:2" });
    find(tree, (node) => node.props?.className === "text-button").props.onClick();
    assert.equal(selections[1], null);
    const html = renderToStaticMarkup(createElement(GraphDetails, { ...props, graph: filterEntityGraph(graph, "Isolated", "", 1) }));
    assert.match(html, /Follow the evidence/);
    assert.doesNotMatch(html, /Alex summary/);
});

test("list view provides native selection buttons with all the graph's connections and safe names", () => {
    const graph = buildEntityGraph(entries);
    const html = renderToStaticMarkup(createElement(GraphList, { graph, selection: { kind: "link", id: "1:2" }, onSelect() {} }));
    assert.match(html, /Entities \(4\)/);
    assert.match(html, /Shared-document connections \(3\)/);
    assert.match(html, /Alex ↔ Priya/);
    assert.match(html, /aria-pressed="true"/);
    const unsafe = buildEntityGraph([{ entity: entity(1, "<script>bad()</script>"), documents: [budget] }]);
    const safe = renderToStaticMarkup(createElement(GraphList, { graph: unsafe, selection: null, onSelect() {} }));
    assert.doesNotMatch(safe, /<script>/);
    assert.match(safe, /&lt;script&gt;/);
});

function dashboardHarness() {
    const state = [];
    const effects = [];
    const pending = [];
    const window = new EventTarget();
    const requests = [];
    let stateCursor;
    let effectCursor;
    const component = load(
        "DashboardPage.tsx",
        {
            react: {
                lazy: () => () => null,
                Suspense: () => null,
                useState: (initial) => {
                    const i = stateCursor++;
                    if (!(i in state)) state[i] = initial;
                    return [
                        state[i],
                        (value) => {
                            state[i] = typeof value === "function" ? value(state[i]) : value;
                        },
                    ];
                },
                useMemo: (factory) => factory(),
                useEffect: (effect, deps) => {
                    const i = effectCursor++;
                    if (!effects[i] || deps.some((dep, index) => dep !== effects[i].deps[index]))
                        pending.push(() => {
                            effects[i]?.cleanup?.();
                            effects[i] = { deps, cleanup: effect() };
                        });
                },
            },
            "./graphApi": { loadEntityGraph: (limit, signal) => new Promise((resolve, reject) => requests.push({ limit, signal, resolve, reject })) },
            "../entities/useEntityRefresh": { ENTITIES_UPDATED_EVENT: "knowledgebridge:entities-updated" },
            "./DashboardUsageOverview": { DashboardUsageOverview: () => null },
            "./GraphDetails": { GraphDetails: () => null },
            "./GraphList": { GraphList: () => null },
            "./GraphRenderBoundary": { GraphRenderBoundary: () => null },
            "./GraphViewSettings": { GraphViewSettings },
        },
        { window, AbortController },
    );
    const render = () => {
        stateCursor = 0;
        effectCursor = 0;
        const tree = component.DashboardPage({ onOpenDetail() {} });
        pending.splice(0).forEach((effect) => effect());
        return tree;
    };
    render();
    return { render, requests, window, cleanup: () => effects.forEach((effect) => effect.cleanup?.()) };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

test("dashboard name filtering passes the same neighborhood to 3D and list views and clears without reloading", async () => {
    const page = dashboardHarness();
    page.requests[0].resolve({ graph: buildEntityGraph(entries), entityLimitReached: false, documentLimitReached: false });
    await flush();
    let tree = page.render();
    find(tree, (node) => node.props?.id === "graph-filter-name").props.onChange({ target: { value: "alex" } });
    tree = page.render();
    const filtered = find(tree, (node) => node.props?.settings && "paused" in node.props).props.graph;
    assert.deepEqual(
        Array.from(filtered.nodes, (node) => node.id),
        [1, 3, 2],
    );
    assert.equal(filtered.links.length, 3);
    find(tree, (node) => node.type === "button" && node.props.children === "List view").props.onClick();
    tree = page.render();
    assert.deepEqual(plain(find(tree, (node) => node.type?.name === "GraphList").props.graph), plain(filtered));
    find(tree, (node) => node.props?.id === "graph-filter-documents").props.onChange({ target: { value: "2" } });
    tree = page.render();
    assert.deepEqual(
        Array.from(find(tree, (node) => node.type?.name === "GraphList").props.graph.nodes, (node) => node.id),
        [1, 2],
    );
    find(tree, (node) => node.type === "button" && node.props.children === "Clear filters").props.onClick();
    tree = page.render();
    assert.equal(find(tree, (node) => node.type?.name === "GraphList").props.graph.nodes.length, 4);
    assert.equal(page.requests.length, 1);
    page.cleanup();
});

test("view settings expose accessible ranges, preserve unrelated preferences, and reset defaults", () => {
    const changes = [];
    const settings = { ...DEFAULT_GRAPH_VIEW, spacing: 150, nodeSize: 180, showLabels: false };
    const tree = GraphViewSettings({ settings, onChange: (next) => changes.push(next) });
    const html = renderToStaticMarkup(createElement(GraphViewSettings, { settings, onChange() {} }));
    assert.match(html, /<details class="graph-view-settings">/);
    assert.match(html, /for="graph-setting-nodeSize"/);
    assert.match(html, /aria-valuetext="180%"/);
    find(tree, (node) => node.props?.id === "graph-setting-nodeSize").props.onChange({ target: { value: "999" } });
    assert.equal(changes[0].nodeSize, 200);
    assert.equal(changes[0].spacing, 150);
    find(tree, (node) => node.type === "input" && node.props.type === "checkbox").props.onChange({ target: { checked: true } });
    assert.equal(changes[1].showLabels, true);
    find(tree, (node) => node.type === "button").props.onClick();
    assert.deepEqual(plain(changes[2]), plain(DEFAULT_GRAPH_VIEW));
    assert.equal(graphViewValue("nodeSize", NaN), DEFAULT_GRAPH_VIEW.nodeSize);
    assert.equal(graphViewValue("spacing", -50), 50);
    assert.equal(graphViewValue("linkOpacity", 57), 55);
    assert.ok(graphLayoutForces(50).distance < graphLayoutForces(200).distance);
    assert.ok(graphLayoutForces(200).strength < graphLayoutForces(50).strength);
});

test("dashboard retains settings and selection across views, and spacing resumes a paused layout", async () => {
    const page = dashboardHarness();
    page.requests[0].resolve({ graph: buildEntityGraph(entries), entityLimitReached: false, documentLimitReached: false });
    await flush();
    let tree = page.render();
    const renderer = (root) => find(root, (node) => node.props?.settings && "paused" in node.props);
    renderer(tree).props.onSelect({ kind: "link", id: "1:2" });
    renderer(tree).props.onPausedChange(true);
    tree = page.render();
    find(tree, (node) => node.type === GraphViewSettings).props.onChange({ ...DEFAULT_GRAPH_VIEW, nodeSize: 180 });
    tree = page.render();
    assert.equal(renderer(tree).props.paused, true, "appearance changes do not restart layout");
    find(tree, (node) => node.type === GraphViewSettings).props.onChange({ ...DEFAULT_GRAPH_VIEW, nodeSize: 180, spacing: 160 });
    tree = page.render();
    assert.equal(renderer(tree).props.paused, false);
    find(tree, (node) => node.type === "button" && node.props.children === "List view").props.onClick();
    tree = page.render();
    assert.equal(find(tree, (node) => node.type === GraphViewSettings).props.settings.nodeSize, 180);
    assert.deepEqual(plain(find(tree, (node) => node.type?.name === "GraphList").props.selection), { kind: "link", id: "1:2" });
    find(tree, (node) => node.type === "button" && node.props.children === "3D graph").props.onClick();
    tree = page.render();
    assert.equal(renderer(tree).props.settings.spacing, 160);
    renderer(tree).props.onSettingsChange({ ...renderer(tree).props.settings, showLabels: false });
    tree = page.render();
    assert.equal(find(tree, (node) => node.type === GraphViewSettings).props.settings.showLabels, false);
    assert.equal(renderer(tree).props.graph.nodes.length, 4);
    assert.equal(renderer(tree).props.graph.links.length, 3);
    assert.equal(page.requests.length, 1, "view preferences do not reload data");
    page.cleanup();
});

test("renderer applies node, label, link, and force settings without mutating source data", () => {
    const forces = [];
    const updated = [];
    let reheated = 0;
    const api = {
        d3Force: (name) => (name === "link" ? { distance: (value) => forces.push([name, value]) } : { strength: (value) => forces.push([name, value]) }),
        d3ReheatSimulation: () => reheated++,
        renderer: () => ({ domElement: new EventTarget() }),
    };
    let refIndex = 0;
    let stateIndex = 0;
    const pending = [];
    class SpriteText {
        constructor(text, height) {
            this.text = text;
            this.height = height;
        }
        position = { set() {} };
        material = {};
    }
    function ForceGraph() {}
    const Canvas = load(
        "EntityGraphCanvas.tsx",
        {
            react: {
                useCallback: (callback) => callback,
                useMemo: (factory) => factory(),
                useRef: () => ({ current: [null, api, false][refIndex++] }),
                useState: () => [stateIndex++ === 0 ? 800 : false, () => {}],
                useEffect: (effect) => pending.push(effect),
            },
            "react-force-graph-3d": { __esModule: true, default: ForceGraph },
            "three-spritetext": { __esModule: true, default: SpriteText },
        },
        { document: new EventTarget() },
    ).default;
    const graph = buildEntityGraph(entries);
    const original = plain(graph);
    const settings = { ...DEFAULT_GRAPH_VIEW, spacing: 200, nodeSize: 150, labelSize: 200, linkWidth: 200, linkOpacity: 80, sizeByDocuments: false };
    const tree = Canvas({ graph, selection: { kind: "link", id: "1:2" }, settings, paused: false, onPausedChange() {}, onSelect() {}, onSettingsChange: (next) => updated.push(next) });
    const props = find(tree, (node) => node.type === ForceGraph).props;
    pending.forEach((effect) => effect());
    assert.deepEqual(forces, [
        ["link", 60],
        ["charge", -120],
    ]);
    assert.equal(reheated, 1);
    assert.equal(props.nodeRelSize, 6);
    assert.equal(props.nodeVal(graph.nodes[0]), 3);
    assert.equal(props.linkWidth(graph.links[0]), 5.6);
    assert.equal(props.linkOpacity, 0.8);
    const label = props.nodeThreeObject(graph.nodes[0]);
    assert.equal(label.text, graph.nodes[0].name);
    assert.equal(label.height, 6.6);
    find(tree, (node) => node.type === "button" && node.props.children === "Hide labels").props.onClick();
    assert.equal(updated[0].showLabels, false);
    props.graphData.nodes[0].x = 123;
    props.graphData.links[0].source = props.graphData.nodes[0];
    assert.deepEqual(plain(graph), original);
});

test("dashboard ignores canceled responses, refreshes extraction changes and cancels on unmount", async () => {
    const page = dashboardHarness();
    assert.equal(page.requests[0].limit, 50);
    page.window.dispatchEvent(new Event("knowledgebridge:entities-updated"));
    page.render();
    assert.equal(page.requests[0].signal.aborted, true);
    page.requests[0].resolve({ graph: buildEntityGraph(entries), entityLimitReached: false, documentLimitReached: false });
    await flush();
    assert.ok(find(page.render(), (node) => node.props?.role === "status" && node.props.children === "Gathering entities and their source documents…"));
    page.requests[1].resolve({ graph: buildEntityGraph([]), entityLimitReached: false, documentLimitReached: false });
    await flush();
    assert.ok(find(page.render(), (node) => node.type === "h3" && node.props.children === "Your knowledge map starts with a document"));
    page.cleanup();
    assert.equal(page.requests[1].signal.aborted, true);
    page.window.dispatchEvent(new Event("knowledgebridge:entities-updated"));
    assert.equal(page.requests.length, 2);
});

test("dashboard errors have a retry and a successful retry exposes list/graph selection", async () => {
    const page = dashboardHarness();
    page.requests[0].reject(new Error("Entity lookup failed"));
    await flush();
    let tree = page.render();
    assert.ok(find(tree, (node) => node.props?.role === "alert"));
    find(tree, (node) => node.type === "button" && node.props.children === "Try loading graph again").props.onClick();
    page.render();
    page.requests[1].resolve({ graph: buildEntityGraph(entries), entityLimitReached: true, documentLimitReached: false });
    await flush();
    tree = page.render();
    assert.ok(find(tree, (node) => node.props?.className === "query-warning graph-limit-note"));
    find(tree, (node) => node.type === "button" && node.props.children === "List view").props.onClick();
    tree = page.render();
    assert.ok(find(tree, (node) => node.type?.name === "GraphList"));
    page.cleanup();
});

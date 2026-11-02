import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ForceGraph3D, { type ForceGraphMethods, type NodeObject } from "react-force-graph-3d";
import SpriteText from "three-spritetext";
import { ENTITY_COLORS, escapeGraphLabel, type EntityGraph, type GraphLink, type GraphNode, type GraphSelection } from "./graphData";
import { graphLayoutForces, type GraphViewOptions } from "./graphViewOptions";

type LinkData = Pick<GraphLink, "id" | "documents">;

export default function EntityGraphCanvas({
    graph,
    selection,
    onSelect,
    settings,
    onSettingsChange,
    paused,
    onPausedChange,
}: {
    graph: EntityGraph;
    selection: GraphSelection;
    onSelect: (selection: GraphSelection) => void;
    settings: GraphViewOptions;
    onSettingsChange: (settings: GraphViewOptions) => void;
    paused: boolean;
    onPausedChange: (paused: boolean) => void;
}) {
    const container = useRef<HTMLDivElement>(null);
    const force = useRef<ForceGraphMethods<GraphNode, LinkData> | undefined>(undefined);
    const fitted = useRef(false);
    const [width, setWidth] = useState(0);
    const [contextLost, setContextLost] = useState(false);
    // The force engine mutates positions and replaces numeric endpoints with node objects.
    // Isolate that mutable state from filters, selection, and the loaded API snapshot.
    const data = useMemo(() => ({ nodes: graph.nodes.map((node) => ({ ...node })), links: graph.links.map((link) => ({ ...link })) }), [graph]);
    const names = useMemo(() => new Map(graph.nodes.map((node) => [node.id, node.name])), [graph]);
    const selectedLinks = new Set(
        graph.links.filter((link) => (selection?.kind === "link" ? link.id === selection.id : selection?.kind === "entity" && (link.source === selection.id || link.target === selection.id))).map((link) => link.id),
    );
    const nodeLabel = useCallback(
        (node: NodeObject<GraphNode>) => {
            const label = new SpriteText(settings.showLabels ? node.name : "", (3.3 * settings.labelSize) / 100, "#ecf3ff");
            label.position.set(0, (8 * settings.nodeSize) / 100, 0);
            label.material.depthWrite = false;
            return label;
        },
        [settings.showLabels, settings.labelSize, settings.nodeSize],
    );

    useEffect(() => {
        const element = container.current;
        if (!element) return;
        const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
        observer.observe(element);
        return () => observer.disconnect();
    }, []);

    const ready = width > 0 && !contextLost;
    useEffect(() => {
        if (!ready) return;
        const api = force.current;
        const link = api?.d3Force("link") as { distance: (distance: number) => unknown } | undefined;
        const charge = api?.d3Force("charge") as { strength: (strength: number) => unknown } | undefined;
        const { distance, strength } = graphLayoutForces(settings.spacing);
        link?.distance(distance);
        charge?.strength(strength);
        fitted.current = false;
        api?.d3ReheatSimulation();
    }, [data, settings.spacing, ready]);

    useEffect(() => {
        const visibility = () => {
            if (document.visibilityState === "hidden") force.current?.pauseAnimation();
            else force.current?.resumeAnimation();
        };
        document.addEventListener("visibilitychange", visibility);
        return () => document.removeEventListener("visibilitychange", visibility);
    }, []);

    useEffect(() => {
        if (!width || contextLost) return;
        const canvas = force.current?.renderer().domElement;
        const lost = () => setContextLost(true);
        canvas?.addEventListener("webglcontextlost", lost);
        return () => canvas?.removeEventListener("webglcontextlost", lost);
    }, [width, contextLost]);

    const focusNode = (node: NodeObject<GraphNode>) => {
        onSelect({ kind: "entity", id: node.id });
        fitted.current = true;
        const x = node.x ?? 0;
        const y = node.y ?? 0;
        const z = node.z ?? 0;
        const distance = Math.hypot(x, y, z);
        const ratio = 1 + 100 / Math.max(distance, 1);
        force.current?.cameraPosition(distance < 1 ? { x: 0, y: 0, z: 100 } : { x: x * ratio, y: y * ratio, z: z * ratio }, { x, y, z }, 650);
    };

    return (
        <div className="graph-canvas-shell">
            <div className="graph-canvas-controls">
                <button type="button" onClick={() => force.current?.zoomToFit(500, 50)}>
                    Fit graph
                </button>
                <button
                    type="button"
                    aria-pressed={paused}
                    onClick={() => {
                        onPausedChange(!paused);
                        if (paused) force.current?.d3ReheatSimulation();
                    }}
                >
                    {paused ? "Resume layout" : "Pause layout"}
                </button>
                <button type="button" aria-pressed={settings.showLabels} onClick={() => onSettingsChange({ ...settings, showLabels: !settings.showLabels })}>
                    {settings.showLabels ? "Hide labels" : "Show labels"}
                </button>
            </div>
            <div className="graph-canvas" ref={container} role="img" aria-label={`Interactive 3D graph with ${graph.nodes.length} entities and ${graph.links.length} shared-document connections. Use List view for keyboard navigation.`}>
                {contextLost ? (
                    <p className="graph-render-message" role="status">
                        The 3D graphics connection was lost. Use List view or refresh the page to try again.
                    </p>
                ) : (
                    width > 0 && (
                        <ForceGraph3D<GraphNode, LinkData>
                            ref={force}
                            graphData={data}
                            width={width}
                            height={560}
                            backgroundColor="#0c1528"
                            controlType="trackball"
                            showNavInfo={false}
                            nodeColor={(node) => ENTITY_COLORS[node.type]}
                            nodeVal={(node) => (settings.sizeByDocuments ? Math.min(8, 2 + Math.sqrt(node.documents.length)) : 3)}
                            nodeRelSize={(4 * settings.nodeSize) / 100}
                            nodeOpacity={0.95}
                            nodeLabel={(node) => escapeGraphLabel(`${node.name} · ${node.type.toLowerCase()} · ${node.documents.length} source documents`)}
                            nodeThreeObjectExtend
                            nodeThreeObject={nodeLabel}
                            linkLabel={(link) =>
                                escapeGraphLabel(
                                    `${names.get(typeof link.source === "object" ? link.source.id : Number(link.source))} ↔ ${names.get(typeof link.target === "object" ? link.target.id : Number(link.target))} · ${link.documents.length} shared documents`,
                                )
                            }
                            linkColor={(link) => (selectedLinks.has(link.id) ? "#75e3ff" : "#59769e")}
                            linkWidth={(link) => ((selectedLinks.has(link.id) ? 2.8 : Math.min(2, 0.3 + Math.sqrt(link.documents.length) * 0.35)) * settings.linkWidth) / 100}
                            linkOpacity={settings.linkOpacity / 100}
                            linkHoverPrecision={4}
                            onNodeClick={focusNode}
                            onLinkClick={(link) => onSelect({ kind: "link", id: link.id })}
                            onBackgroundClick={() => onSelect(null)}
                            cooldownTicks={paused ? 0 : 100}
                            warmupTicks={30}
                            onEngineStop={() => {
                                if (!fitted.current) {
                                    force.current?.zoomToFit(650, 55);
                                    fitted.current = true;
                                }
                            }}
                        />
                    )
                )}
            </div>
            <p className="graph-navigation-help">Drag the background to rotate · Scroll / pinch to zoom · Right-drag to pan · Drag a node to reposition · Select a link to view its documents</p>
        </div>
    );
}

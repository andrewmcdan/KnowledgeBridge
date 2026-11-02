export interface GraphViewOptions {
    spacing: number;
    nodeSize: number;
    linkWidth: number;
    linkOpacity: number;
    labelSize: number;
    showLabels: boolean;
    sizeByDocuments: boolean;
}

export const DEFAULT_GRAPH_VIEW: Readonly<GraphViewOptions> = {
    spacing: 100,
    nodeSize: 100,
    linkWidth: 100,
    linkOpacity: 55,
    labelSize: 100,
    showLabels: true,
    sizeByDocuments: true,
};

export const GRAPH_VIEW_RANGES = {
    spacing: { label: "Layout spacing", min: 50, max: 200, step: 10 },
    nodeSize: { label: "Node size", min: 50, max: 200, step: 10 },
    linkWidth: { label: "Link thickness", min: 50, max: 250, step: 10 },
    linkOpacity: { label: "Link opacity", min: 10, max: 100, step: 5 },
    labelSize: { label: "Label size", min: 50, max: 200, step: 10 },
} as const;

export type GraphViewRange = keyof typeof GRAPH_VIEW_RANGES;

export function graphViewValue(key: GraphViewRange, value: number): number {
    const { min, max, step } = GRAPH_VIEW_RANGES[key];
    if (!Number.isFinite(value)) return DEFAULT_GRAPH_VIEW[key];
    return Math.min(max, Math.max(min, min + Math.round((value - min) / step) * step));
}

// These forces control layout density without changing which entities or links are shown.
export function graphLayoutForces(spacing: number): { distance: number; strength: number } {
    const scale = graphViewValue("spacing", spacing) / 100;
    return { distance: 30 * scale, strength: -30 * scale * scale };
}

import type { DetectedEntity, EntityType, RelatedDocument } from "../entities/entitiesApi";

export interface EntityDocuments {
    entity: DetectedEntity;
    documents: RelatedDocument[];
}

export interface GraphNode extends DetectedEntity {
    documents: RelatedDocument[];
}

export interface GraphLink {
    id: string;
    source: number;
    target: number;
    documents: RelatedDocument[];
}

export interface EntityGraph {
    nodes: GraphNode[];
    links: GraphLink[];
}

export const ENTITY_COLORS: Record<EntityType, string> = {
    PERSON: "#4da5ff",
    COMPANY: "#b49aff",
    PROJECT: "#36d4b0",
    TEAM: "#ffbd66",
};

export const ENTITY_LABELS: Record<EntityType, string> = {
    PERSON: "People",
    COMPANY: "Companies",
    PROJECT: "Projects",
    TEAM: "Teams",
};

/** Undirected co-occurrence edges: a document contributes once to every pair of its entities. */
export function buildEntityGraph(entries: EntityDocuments[]): EntityGraph {
    const nodes = new Map<number, GraphNode>();
    const documents = new Map<number, { document: RelatedDocument; entities: Set<number> }>();
    for (const entry of entries) {
        const unique = new Map([...(nodes.get(entry.entity.id)?.documents ?? []), ...entry.documents].map((document) => [document.id, document]));
        nodes.set(entry.entity.id, { ...entry.entity, documents: [...unique.values()].sort(compareDocuments) });
        for (const document of unique.values()) {
            const occurrence = documents.get(document.id) ?? { document, entities: new Set<number>() };
            occurrence.entities.add(entry.entity.id);
            documents.set(document.id, occurrence);
        }
    }
    const links = new Map<string, GraphLink>();
    for (const { document, entities } of documents.values()) {
        const ids = [...entities].sort((a, b) => a - b);
        for (let i = 0; i < ids.length; i++) {
            for (let j = i + 1; j < ids.length; j++) {
                const source = ids[i];
                const target = ids[j];
                const id = `${source}:${target}`;
                const link = links.get(id) ?? { id, source, target, documents: [] };
                link.documents.push(document);
                links.set(id, link);
            }
        }
    }
    return {
        nodes: [...nodes.values()].sort((a, b) => a.name.localeCompare(b.name) || a.id - b.id),
        links: [...links.values()].map((link) => ({ ...link, documents: link.documents.sort(compareDocuments) })).sort((a, b) => b.documents.length - a.documents.length || a.id.localeCompare(b.id)),
    };
}

function compareDocuments(a: RelatedDocument, b: RelatedDocument): number {
    return a.title.localeCompare(b.title) || a.id - b.id;
}

export function filterEntityGraph(graph: EntityGraph, query: string, type: string, minimumDocuments: number): EntityGraph {
    const term = query.trim().toLocaleLowerCase();
    const nodes = graph.nodes.filter((node) => (!type || node.type === type) && (!term || node.name.toLocaleLowerCase().includes(term)));
    const ids = new Set(nodes.map((node) => node.id));
    return { nodes, links: graph.links.filter((link) => ids.has(link.source) && ids.has(link.target) && link.documents.length >= minimumDocuments) };
}

export function graphDocumentCount(graph: EntityGraph): number {
    return new Set(graph.nodes.flatMap((node) => node.documents.map((document) => document.id))).size;
}

export type GraphSelection = { kind: "entity"; id: number } | { kind: "link"; id: string } | null;

/** The graph library's tooltip strings are HTML, unlike React text children. */
export function escapeGraphLabel(text: string): string {
    return text.replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character] ?? character);
}

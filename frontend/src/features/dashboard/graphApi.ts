import { getEntities, getEntityDocuments } from "../entities/entitiesApi";
import { buildEntityGraph, type EntityDocuments, type EntityGraph } from "./graphData";

export interface GraphSnapshot {
    graph: EntityGraph;
    entityLimitReached: boolean;
    documentLimitReached: boolean;
}

/** Reuse the authenticated Spring boundary; bound fan-out and cancel abandoned loads. */
export async function loadEntityGraph(limit: number, signal: AbortSignal): Promise<GraphSnapshot> {
    const entities = await getEntities("", "", limit, signal);
    const entries: EntityDocuments[] = [];
    let cursor = 0;
    let failed = false;
    async function worker() {
        while (!failed && !signal.aborted && cursor < entities.length) {
            const entity = entities[cursor++];
            try {
                const documents = await getEntityDocuments(entity.id, signal);
                entries.push({ entity, documents });
            } catch (error: unknown) {
                failed = true;
                throw error;
            }
        }
    }
    await Promise.all(Array.from({ length: Math.min(4, entities.length) }, worker));
    signal.throwIfAborted();
    return {
        graph: buildEntityGraph(entries),
        entityLimitReached: entities.length === limit,
        documentLimitReached: entries.some((entry) => entry.documents.length >= 100 || entry.entity.documentCount > entry.documents.length),
    };
}

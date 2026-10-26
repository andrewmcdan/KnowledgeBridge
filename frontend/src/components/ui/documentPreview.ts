import { parseDocument } from "yaml";
import type { DetectedEntity, EntityType } from "../../features/entities/entitiesApi";

export interface MetadataEntry {
    label: string;
    value: string;
}

export interface ParsedDocument {
    markdown: string;
    metadata: MetadataEntry[];
    rawMetadata: string | null;
    metadataError: string | null;
}

function metadataValue(value: unknown): string {
    if (value instanceof Map) return JSON.stringify(Object.fromEntries(Array.from(value, ([key, child]) => [String(key), metadataValue(child)])), null, 2);
    if (Array.isArray(value)) return value.map(metadataValue).join(", ");
    return value === null || value === undefined ? "—" : String(value);
}

export function parseDocumentMarkdown(text: string): ParsedDocument {
    const normalized = text.replace(/^\uFEFF/, "").replace(/\r\n/g, "\n");
    const frontMatter = /^---[ \t]*\n([\s\S]*?)\n(?:---|\.\.\.)[ \t]*(?:\n|$)/.exec(normalized);
    const result: ParsedDocument = { markdown: normalized, metadata: [], rawMetadata: null, metadataError: null };
    if (!frontMatter) return result;
    result.markdown = normalized.slice(frontMatter[0].length);
    result.rawMetadata = frontMatter[1];
    try {
        if (frontMatter[1].length > 64_000) throw new Error("Metadata exceeds preview limit");
        const document = parseDocument(frontMatter[1], { schema: "failsafe", uniqueKeys: true });
        if (document.errors.length) throw new Error("Invalid metadata");
        const data: unknown = document.toJS({ mapAsMap: true, maxAliasCount: 0 });
        result.metadata = data instanceof Map ? Array.from(data, ([key, value]) => ({ label: String(key).replace(/[_-]+/g, " "), value: metadataValue(value) })) : data === null ? [] : [{ label: "Value", value: metadataValue(data) }];
    } catch {
        result.metadataError = "This metadata could not be formatted. The original metadata is shown below.";
    }
    return result;
}

const ENTITY_GROUPS: { type: EntityType; label: string }[] = [
    { type: "PERSON", label: "People" },
    { type: "COMPANY", label: "Companies" },
    { type: "PROJECT", label: "Projects" },
    { type: "TEAM", label: "Teams" },
];

export function groupDocumentEntities(entities: DetectedEntity[]) {
    return ENTITY_GROUPS.map((group) => ({
        ...group,
        entities: entities.filter((entity) => entity.type === group.type).sort((first, second) => first.name.localeCompare(second.name) || first.id - second.id),
    })).filter((group) => group.entities.length > 0);
}

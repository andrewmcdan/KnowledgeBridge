import type { Detail } from "../../types/models";
import type { SynthesisResponse } from "./askAiApi";

export function citationDetail(citation: SynthesisResponse["citations"][number]): Detail | null {
    if (!/^[1-9]\d*$/.test(citation.itemKey)) return null;
    const id = Number(citation.itemKey);
    if (!Number.isSafeInteger(id)) return null;
    return { id, title: citation.title || `Document ${id}`, text: "" };
}

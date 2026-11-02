import type { Detail } from "../../types/models";
import type { SynthesisResponse } from "./askAiApi";

export function citationDetail(citation: SynthesisResponse["citations"][number]): Detail | null {
    if (!/^[1-9]\d*$/.test(citation.itemKey)) return null;
    const id = Number(citation.itemKey);
    if (!Number.isSafeInteger(id)) return null;
    return { id, title: citation.title || `Document ${id}`, text: "" };
}

export function citedPassages(answer: string, number: number, source: string): string[] {
    const text = answer.replace(/(`{3,}|~{3,})[^\n]*\n[\s\S]*?\1/g, "").replace(/(`+)[\s\S]*?\1/g, "");
    const blocks = text
        .split(/\n\s*\n/)
        .map((block) => block.trim())
        .filter(Boolean);
    const cited = blocks.filter((block) => block.includes(`[${number}]`) || (source && (block.includes(`[${source}]`) || block.includes(`](${source})`))));
    // An uncited answer can still be compared as a whole. Never borrow another source's cited paragraph.
    return (cited.length ? cited : /\[(?:\d+|knowledgebridge\/[^\]]+)\]|\]\(knowledgebridge\/[^)]+\)/.test(text) ? [] : blocks).slice(0, 16);
}

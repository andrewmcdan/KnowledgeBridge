import type { DocumentFocus } from "../../types/models";

const STOP_WORDS = new Set(
    "a an and are as at be been being but by can could did do does for from had has have how i if in into is it its may more most no not of on or our per should than that the their them then there these they this those to use used using was we were what when where which who why will with would you your policy document current source sources answer citation".split(
        " ",
    ),
);

function words(text: string): string[] {
    return (
        text
            .slice(0, 32_000)
            .normalize("NFKC")
            .toLowerCase()
            .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
            .replace(/\[(?:\d+|knowledgebridge\/[^\]]+)\]/g, "")
            .replace(/[*_~`]/g, "")
            .replace(/\b\d{1,3}(?:,\d{3})+(?:\.\d+)?\b/g, (number) => number.replaceAll(",", ""))
            .match(/\p{N}+(?:\.\p{N}+)?|[\p{L}\p{N}]+/gu) ?? []
    ).slice(0, 4000);
}

function meaningful(tokens: string[]): Set<string> {
    return new Set(tokens.filter((word) => !STOP_WORDS.has(word) && (word.length > 1 || /^\d/.test(word))));
}

/** Select related passages, not factual entailment: synthesis supplies no engine quote offsets. */
export function matchReferencePassages(passages: readonly string[], focus: DocumentFocus): number[] {
    return rankReferencePassages(passages, focus).sort((a, b) => a - b);
}

export function rankReferencePassages(passages: readonly string[], focus: DocumentFocus, headings: readonly string[] = []): number[] {
    const evidence = focus.passages
        .slice(0, 16)
        .map((text) => {
            const tokens = words(text);
            return { normalized: tokens.join(" "), terms: meaningful(tokens) };
        })
        .filter((entry) => entry.terms.size >= 3);
    if (!evidence.length) return [];
    const question = meaningful(words(focus.question ?? ""));
    return passages
        .map((text, index) => {
            const tokens = words(text);
            const terms = meaningful(tokens);
            const normalized = tokens.join(" ");
            let score = 0;
            for (const entry of evidence) {
                const shared = [...terms].filter((term) => entry.terms.has(term)).length;
                if (shared < 3) continue;
                const exact = ` ${entry.normalized} `.includes(` ${normalized} `) || ` ${normalized} `.includes(` ${entry.normalized} `);
                const coverage = shared / Math.max(1, Math.min(terms.size, entry.terms.size));
                if (exact || (shared >= 4 && coverage >= (focus.kind === "search" ? 0.7 : 0.5))) score = Math.max(score, (exact ? 2 : 1) + coverage);
            }
            if (score) {
                const context = new Set([...terms, ...meaningful(words(headings[index] ?? ""))]);
                score += (3 * [...context].filter((term) => question.has(term)).length) / Math.max(1, question.size);
            }
            return { index, score };
        })
        .filter((entry) => entry.score > 0)
        .sort((a, b) => b.score - a.score || a.index - b.index)
        .slice(0, 6)
        .map((entry) => entry.index);
}

interface HtmlNode {
    type: string;
    tagName?: string;
    value?: string;
    properties?: Record<string, unknown>;
    children?: HtmlNode[];
}

function visibleText(node: HtmlNode): string {
    if (node.type === "text") return node.value ?? "";
    if (node.tagName === "br") return " ";
    return node.children?.map(visibleText).join(node.tagName === "tr" ? " " : "") ?? "";
}

export function rehypeReferencePassages({ focus, active = 0 }: { focus: DocumentFocus; active?: number }) {
    return (tree: HtmlNode) => {
        const blocks: HtmlNode[] = [];
        const contexts: string[] = [];
        const headings = new Map<number, string>();
        const visit = (node: HtmlNode) => {
            if (/^h[1-6]$/.test(node.tagName ?? "")) {
                const level = Number(node.tagName?.slice(1));
                for (const key of headings.keys()) if (key >= level) headings.delete(key);
                headings.set(level, visibleText(node));
            }
            const paragraph = node.tagName === "p" || node.tagName === "pre";
            const tableRow = node.tagName === "tr" && node.children?.some((child) => child.tagName === "td");
            const listItem = node.tagName === "li" && !node.children?.some((child) => ["p", "ul", "ol"].includes(child.tagName ?? ""));
            if (paragraph || tableRow || listItem) {
                blocks.push(node);
                contexts.push([...headings.values()].join(" "));
                return;
            }
            node.children?.forEach(visit);
        };
        visit(tree);
        const ranked = rankReferencePassages(blocks.map(visibleText), focus, contexts);
        const matches = [...ranked].sort((a, b) => a - b);
        matches.forEach((index, position) => {
            blocks[index].properties = {
                ...blocks[index].properties,
                className: ["document-passage-match", ...(position === active ? ["document-passage-active"] : [])],
                "data-reference-passage": position,
                ...(index === ranked[0] ? { "data-reference-primary": true } : {}),
            };
        });
    };
}

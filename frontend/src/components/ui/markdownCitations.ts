export interface CitationReference {
    number: number;
    source: string;
}

interface MarkdownNode {
    type: string;
    value?: string;
    url?: string;
    children?: MarkdownNode[];
}

export function remarkCitationLinks(citations: readonly CitationReference[] = []) {
    const references = new Map<string, number>();
    for (const citation of citations) {
        references.set(String(citation.number), citation.number);
        if (!references.has(citation.source)) references.set(citation.source, citation.number);
    }
    return (tree: MarkdownNode) => {
        if (!references.size) return;
        const visit = (node: MarkdownNode) => {
            // Do not turn code, image descriptions, or existing links into nested links.
            if (!node.children || ["link", "linkReference", "image", "imageReference", "code", "inlineCode"].includes(node.type)) return;
            node.children = node.children.flatMap((child) => {
                if (child.type !== "text" || !child.value) {
                    visit(child);
                    return [child];
                }
                const text = child.value;
                const parts: MarkdownNode[] = [];
                let end = 0;
                for (const match of text.matchAll(/\[([^\s[\]]+)\]/g)) {
                    const number = references.get(match[1]);
                    if (number === undefined) continue;
                    if (match.index > end) parts.push({ type: "text", value: text.slice(end, match.index) });
                    parts.push({ type: "link", url: `#citation-${number}`, children: [{ type: "text", value: `[${number}]` }] });
                    end = match.index + match[0].length;
                }
                if (!parts.length) return [child];
                if (end < text.length) parts.push({ type: "text", value: text.slice(end) });
                return parts;
            });
        };
        visit(tree);
    };
}

import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { remarkCitationLinks, type CitationReference } from "./markdownCitations";

export interface MarkdownCitation extends CitationReference {
    title: string;
    onOpen: () => void;
}

interface MarkdownContentProps {
    text: string;
    label?: string;
    className?: string;
    citations?: MarkdownCitation[];
}

export function MarkdownContent({ text, label = "Document content", className = "document-markdown", citations = [] }: MarkdownContentProps) {
    return (
        <article className={`markdown-content ${className}`} aria-label={label}>
            <Markdown
                remarkPlugins={[remarkGfm, [remarkCitationLinks, citations]]}
                skipHtml
                components={{
                    a: ({ href, children }) => {
                        const citation = citations.find((entry) => href === `#citation-${entry.number}` || (entry.source && href === entry.source));
                        return citation ? (
                            <button className="inline-citation" type="button" aria-label={`Open citation ${citation.number}: ${citation.title}`} onClick={citation.onOpen}>
                                {children}
                            </button>
                        ) : (
                            <a href={href} target={href?.startsWith("#") ? undefined : "_blank"} rel="noopener noreferrer">
                                {children}
                            </a>
                        );
                    },
                    img: ({ src, alt }) => (
                        <a href={src} target="_blank" rel="noopener noreferrer">
                            {alt ? `Image: ${alt}` : "View image"}
                        </a>
                    ),
                    table: ({ children }) => (
                        <div className="markdown-table-scroll" role="region" aria-label="Scrollable table" tabIndex={0}>
                            <table>{children}</table>
                        </div>
                    ),
                }}
            >
                {text}
            </Markdown>
        </article>
    );
}

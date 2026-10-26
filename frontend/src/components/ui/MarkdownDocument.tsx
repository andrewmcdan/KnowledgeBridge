import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";

export function MarkdownDocument({ text }: { text: string }) {
    return (
        <article className="document-markdown" aria-label="Document content">
            <Markdown
                remarkPlugins={[remarkGfm]}
                skipHtml
                components={{
                    a: ({ href, children }) => (
                        <a href={href} target={href?.startsWith("#") ? undefined : "_blank"} rel="noopener noreferrer">
                            {children}
                        </a>
                    ),
                    img: ({ src, alt }) => (
                        <a href={src} target="_blank" rel="noopener noreferrer">
                            {alt ? `Image: ${alt}` : "View image"}
                        </a>
                    ),
                    table: ({ children }) => (
                        <div className="document-table-scroll" role="region" aria-label="Document table" tabIndex={0}>
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

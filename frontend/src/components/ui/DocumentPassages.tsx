import { useCallback, useEffect, useRef, useState } from "react";
import type { DocumentFocus } from "../../types/models";
import { MarkdownContent } from "./MarkdownContent";

export function DocumentPassages({ text, focus }: { text: string; focus: DocumentFocus }) {
    const container = useRef<HTMLDivElement | null>(null);
    const [count, setCount] = useState<number | null>(null);
    const [active, setActive] = useState(0);
    const attach = useCallback(
        (element: HTMLDivElement | null) => {
            container.current = element;
            if (element) {
                setCount(element.querySelectorAll("[data-reference-passage]").length);
                const primary = element.querySelector("[data-reference-primary]")?.getAttribute("data-reference-passage");
                setActive(primary ? Number(primary) : 0);
            }
        },
        // Reattach when content/context changes: the count comes from the rendered Markdown DOM.
        // oxlint-disable-next-line react-hooks/exhaustive-deps
        [text, focus],
    );

    useEffect(() => {
        container.current?.querySelectorAll<HTMLElement>("[data-reference-passage]")[active]?.scrollIntoView({ block: "center", inline: "nearest", behavior: "instant" });
    }, [text, focus, active]);

    return (
        <div ref={attach} className="document-passages">
            <section className="document-highlight-toolbar" aria-label="Reference passages">
                <div>
                    <strong>{focus.kind === "search" ? "Search excerpt matches" : "Related answer passages"}</strong>
                    <p role="status">{count === null ? "Locating passages…" : count ? `Passage ${active + 1} of ${count}` : "No matching passage found in the current document. Showing the full document."}</p>
                    {focus.kind === "synthesis" && !!count && <small>Matched by text similarity. Review the source in context.</small>}
                </div>
                {!!count && (
                    <div className="document-highlight-actions" role="group" aria-label="Navigate matching passages">
                        <button type="button" className="secondary-button" disabled={count < 2} onClick={() => setActive((value) => (value + count - 1) % count)}>
                            Previous passage
                        </button>
                        <button type="button" className="secondary-button" disabled={count < 2} onClick={() => setActive((value) => (value + 1) % count)}>
                            Next passage
                        </button>
                    </div>
                )}
            </section>
            <MarkdownContent text={text} referenceFocus={focus} activePassage={active} />
        </div>
    );
}

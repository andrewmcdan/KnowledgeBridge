import { Icon } from "../../components/ui/Icon";
import { MarkdownContent } from "../../components/ui/MarkdownContent";
import { parseDocumentMarkdown } from "../../components/ui/documentPreview";
import type { Detail } from "../../types/models";
import { citationDetail } from "./citations";
import type { AskAiMode, QueryResult, SearchResponse, SynthesisResponse } from "./askAiApi";

interface AnswerPanelProps {
    mode: AskAiMode;
    result: QueryResult | null;
    submitting: boolean;
    error: string | null;
    onOpenDetail: (detail: Detail) => void;
}

export function AnswerPanel({ mode, result, submitting, error, onOpenDetail }: AnswerPanelProps) {
    const isSynthesis = result?.mode === "Synthesis";

    return (
        <section className="answer-panel panel" aria-live="polite">
            <div className="answer-body">
                <div className="section-heading">
                    <h2>
                        <Icon name={mode === "Synthesis" ? "spark" : "search"} />
                        {mode === "Synthesis" ? "AI Answer" : "Search Results"}
                    </h2>
                    {isSynthesis && (
                        <span className="grounding">
                            <Icon name="shield" size={17} /> {result.response.aiGenerated ? "AI-generated with citations" : "Extractive fallback"}
                        </span>
                    )}
                </div>
                {submitting && <p>Searching indexed knowledge…</p>}
                {!submitting && error && (
                    <p className="query-error" role="alert">
                        {error}
                    </p>
                )}
                {!submitting && !error && !result && <p>Ask a question to search indexed knowledge or generate a cited AI answer.</p>}
                {!submitting && !error && result?.mode === "Search" && <SearchResults result={result.response} onOpenDetail={onOpenDetail} />}
                {!submitting && !error && result?.mode === "Synthesis" && <SynthesisAnswer result={result.response} onOpenDetail={onOpenDetail} />}
            </div>
        </section>
    );
}

function SearchResults({ result, onOpenDetail }: { result: SearchResponse; onOpenDetail: (detail: Detail) => void }) {
    return (
        <>
            {result.retrieval.degraded && <p className="query-warning">Some semantic retrieval signals were unavailable for this search.</p>}
            <p className="search-results-summary">{result.hits.length ? `${result.hits.length} ${result.hits.length === 1 ? "result" : "results"} · Ranked by relevance` : "No indexed knowledge matched this question."}</p>
            <div className="query-results">
                {result.hits.map((hit, index) => {
                    const detail = citationDetail(hit);
                    const title = hit.title || detail?.title || "Untitled document";
                    const excerpt = parseDocumentMarkdown(hit.snippet).markdown.trim();
                    return (
                        <article className="query-hit" key={`${hit.itemKey}-${index}`}>
                            <header className="search-hit-header">
                                <span className="search-hit-rank" aria-label={`Result ${index + 1}`}>
                                    {index + 1}
                                </span>
                                <div className="search-hit-heading">
                                    <span className="search-hit-type">
                                        <Icon name="file" size={14} />
                                        {hit.documentType.replace(/[-_]+/g, " ") || "Document"}
                                    </span>
                                    <h3>{title}</h3>
                                </div>
                            </header>
                            {excerpt ? (
                                <div className="search-hit-excerpt" role="region" aria-label={`Matching excerpt: ${title}`} tabIndex={0}>
                                    <MarkdownContent text={excerpt} label="Matching excerpt" className="search-excerpt-markdown" />
                                </div>
                            ) : (
                                <p className="search-hit-empty">No text excerpt available.</p>
                            )}
                            <footer className="search-hit-footer">
                                <span>Matching excerpt</span>
                                {detail ? (
                                    <button className="secondary-button search-view-document" type="button" aria-label={`View document: ${title}`} onClick={() => onOpenDetail(detail)}>
                                        View document <Icon name="chevron" size={15} />
                                    </button>
                                ) : (
                                    <span>Document preview unavailable</span>
                                )}
                            </footer>
                        </article>
                    );
                })}
            </div>
        </>
    );
}

function SynthesisAnswer({ result, onOpenDetail }: { result: SynthesisResponse; onOpenDetail: (detail: Detail) => void }) {
    const sources = result.citations.map((citation, index) => ({ citation, detail: citationDetail(citation), number: index + 1 }));
    const links = sources.flatMap(({ citation, detail, number }) => (detail ? [{ number, source: citation.externalId, title: detail.title, onOpen: () => onOpenDetail(detail) }] : []));
    return (
        <>
            <MarkdownContent text={result.answer} label="AI answer" className="synthesis-answer" citations={links} />
            {result.gaps.length > 0 && <p className="query-warning">Knowledge gaps: {result.gaps.join(" ")}</p>}
            {result.warnings.length > 0 && <p className="query-warning">Warnings: {result.warnings.join(" ")}</p>}
            <div className="query-usage" aria-label="Query token usage">
                {result.cacheHit ? (
                    <p>Cached answer · 0 new synthesis tokens</p>
                ) : result.usage ? (
                    <p>
                        {result.usage.estimated ? "Estimated" : "Engine-reported"} synthesis tokens: {(result.usage.inputTokens ?? 0).toLocaleString()} input + {(result.usage.outputTokens ?? 0).toLocaleString()} output ={" "}
                        {((result.usage.inputTokens ?? 0) + (result.usage.outputTokens ?? 0)).toLocaleString()} total.
                    </p>
                ) : (
                    <p>No synthesis token usage was reported for this response.</p>
                )}
            </div>
            <h3 className="citation-heading">Citations ({result.citations.length})</h3>
            {result.citations.length ? (
                <ol className="query-citations">
                    {sources.map(({ citation, detail, number }) => (
                        <li key={`${citation.externalId}-${number}`}>
                            {detail ? (
                                <button className="citation-document-link" type="button" aria-label={`Open document: ${detail.title}`} onClick={() => onOpenDetail(detail)}>
                                    {detail.title}
                                </button>
                            ) : (
                                <strong title="Document preview unavailable">{citation.title || citation.itemKey}</strong>
                            )}
                            {citation.documentType && <span>{citation.documentType}</span>}
                        </li>
                    ))}
                </ol>
            ) : (
                <p>No source citations were returned for this answer.</p>
            )}
        </>
    );
}

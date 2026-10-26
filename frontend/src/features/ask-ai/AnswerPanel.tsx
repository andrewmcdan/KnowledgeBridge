import { Icon } from "../../components/ui/Icon";
import { MarkdownContent } from "../../components/ui/MarkdownContent";
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
                {!submitting && !error && result?.mode === "Search" && <SearchResults result={result.response} />}
                {!submitting && !error && result?.mode === "Synthesis" && <SynthesisAnswer result={result.response} onOpenDetail={onOpenDetail} />}
            </div>
        </section>
    );
}

function SearchResults({ result }: { result: SearchResponse }) {
    if (!result.hits.length) return <p>No indexed knowledge matched this question.</p>;

    return (
        <>
            {result.retrieval.degraded && <p className="query-warning">Some semantic retrieval signals were unavailable for this search.</p>}
            <div className="query-results">
                {result.hits.map((hit) => (
                    <article className="query-hit" key={`${hit.externalId}-${hit.snippet}`}>
                        <strong>{hit.title}</strong>
                        <small>{hit.documentType}</small>
                        <p>{hit.snippet}</p>
                    </article>
                ))}
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

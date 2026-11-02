import { Icon } from "../../components/ui/Icon";
import { MarkdownContent } from "../../components/ui/MarkdownContent";
import { parseDocumentMarkdown } from "../../components/ui/documentPreview";
import type { Detail } from "../../types/models";
import { citationDetail, citedPassages } from "./citations";
import type { AskAiMode, QueryResult, SearchResponse, SynthesisResponse } from "./askAiApi";

interface AnswerPanelProps {
    mode: AskAiMode;
    result: QueryResult | null;
    submitting: boolean;
    error: string | null;
    pendingQuestion?: string | null;
    onOpenDetail: (detail: Detail) => void;
}

export function AnswerPanel({ mode, result, submitting, error, pendingQuestion, onOpenDetail }: AnswerPanelProps) {
    const isSynthesis = result?.mode === "Synthesis";
    const displayedMode = result?.mode ?? mode;
    const displayedQuestion = result?.question ?? (submitting || error ? pendingQuestion : null);

    return (
        <section className="answer-panel panel chat-answer-panel" aria-live="polite">
            <div className="answer-body">
                <div className="section-heading">
                    <h2>
                        <Icon name={displayedMode === "Synthesis" ? "spark" : "search"} />
                        {displayedMode === "Synthesis" ? "AI Answer" : "Search Results"}
                    </h2>
                    {isSynthesis && (
                        <span className="grounding">
                            <Icon name="shield" size={17} /> {result.response.aiGenerated ? "AI-generated with citations" : "Extractive fallback"}
                        </span>
                    )}
                </div>
                {displayedQuestion && (
                    <div className="chat-user-message">
                        <span>You</span>
                        <p>{displayedQuestion}</p>
                    </div>
                )}
                {submitting && <p>Searching indexed knowledge…</p>}
                {!submitting && error && (
                    <p className="query-error" role="alert">
                        {error}
                    </p>
                )}
                {!submitting && !error && !result && (
                    <div className="chat-empty-state">
                        <span aria-hidden="true">✦</span>
                        <h3>How can I help with your knowledge?</h3>
                        <p>Ask a question to search indexed documents or generate a cited AI answer.</p>
                    </div>
                )}
                {!submitting && !error && result?.mode === "Search" && <SearchResults result={result.response} question={result.question} onOpenDetail={onOpenDetail} />}
                {!submitting && !error && result?.mode === "Synthesis" && <SynthesisAnswer result={result.response} question={result.question} onOpenDetail={onOpenDetail} />}
            </div>
        </section>
    );
}

function SearchResults({ result, question, onOpenDetail }: { result: SearchResponse; question?: string; onOpenDetail: (detail: Detail) => void }) {
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
                                    <button
                                        className="secondary-button search-view-document"
                                        type="button"
                                        aria-label={`View document: ${title}`}
                                        onClick={() => onOpenDetail({ ...detail, focus: { kind: "search", passages: [excerpt], question } })}
                                    >
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

function SynthesisAnswer({ result, question, onOpenDetail }: { result: SynthesisResponse; question?: string; onOpenDetail: (detail: Detail) => void }) {
    const sources = result.citations.map((citation, index) => {
        const detail = citationDetail(citation);
        return { citation, number: index + 1, detail: detail ? { ...detail, focus: { kind: "synthesis" as const, passages: citedPassages(result.answer, index + 1, citation.externalId), question } } : null };
    });
    const links = sources.flatMap(({ citation, detail, number }) => (detail ? [{ number, source: citation.externalId, title: detail.title, onOpen: () => onOpenDetail(detail) }] : []));
    return (
        <>
            <MarkdownContent text={result.answer} label="AI answer" className="synthesis-answer" citations={links} />
            {result.gaps.length > 0 && <p className="query-warning">Knowledge gaps: {result.gaps.join(" ")}</p>}
            {result.warnings.length > 0 && <p className="query-warning">Warnings: {result.warnings.join(" ")}</p>}
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

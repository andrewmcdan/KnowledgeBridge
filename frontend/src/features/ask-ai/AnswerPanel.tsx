import { Icon } from "../../components/ui/Icon";
import type { AskAiMode, QueryResult, SearchResponse, SynthesisResponse } from "./askAiApi";

interface AnswerPanelProps {
    mode: AskAiMode;
    result: QueryResult | null;
    submitting: boolean;
    error: string | null;
}

export function AnswerPanel({ mode, result, submitting, error }: AnswerPanelProps) {
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
                {!submitting && !error && result?.mode === "Synthesis" && <SynthesisAnswer result={result.response} />}
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

function SynthesisAnswer({ result }: { result: SynthesisResponse }) {
    return (
        <>
            <p className="synthesis-answer">{result.answer}</p>
            {result.gaps.length > 0 && <p className="query-warning">Knowledge gaps: {result.gaps.join(" ")}</p>}
            {result.warnings.length > 0 && <p className="query-warning">Warnings: {result.warnings.join(" ")}</p>}
            <h3 className="citation-heading">Citations ({result.citations.length})</h3>
            {result.citations.length ? (
                <ol className="query-citations">
                    {result.citations.map((citation) => (
                        <li key={citation.externalId}>
                            <strong>{citation.title ?? citation.itemKey}</strong>
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

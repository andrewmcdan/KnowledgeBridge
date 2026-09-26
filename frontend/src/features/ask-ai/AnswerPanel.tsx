import { approvalSteps } from "../../data/demoData";
import type { DocumentItem } from "../../types/models";
import type { Detail } from "../../types/models";
import { DocumentRow } from "../../components/ui/DocumentRow";
import { Icon } from "../../components/ui/Icon";
import { CitationList } from "./CitationList";

interface AnswerPanelProps {
    mode: string;
    filteredDocuments: DocumentItem[];
    expanded: boolean;
    feedback: string;
    saved: string[];
    onToggleExpanded: () => void;
    onFeedback: (value: string) => void;
    onOpenDetail: (detail: Detail) => void;
    onToggleSaved: (title: string) => void;
    onNotice: (notice: string) => void;
}

export function AnswerPanel({ mode, filteredDocuments, expanded, feedback, saved, onToggleExpanded, onFeedback, onOpenDetail, onToggleSaved, onNotice }: AnswerPanelProps) {
    const copyAnswer = () => {
        void navigator.clipboard
            .writeText(`Sample answer\n${approvalSteps.map(([title, text], index) => `${index + 1}. ${title}: ${text}`).join("\n")}`)
            .then(() => onNotice("Sample answer copied."))
            .catch(() => onNotice("Clipboard access is unavailable. Select the answer text to copy it."));
    };
    return (
        <section className="answer-panel panel">
            <div className="answer-body">
                <div className="section-heading">
                    <h2>
                        <Icon name={mode === "Synthesis" ? "spark" : "search"} />
                        {mode === "Synthesis" ? "AI Answer" : "Search Results"}
                    </h2>
                    <span className="grounding">
                        <Icon name="shield" size={17} /> Sample content · live AI not connected
                    </span>
                </div>
                {mode === "Synthesis" ? (
                    <>
                        <p>For software purchases over $5,000, Acme Corporation requires a multi-step approval process to ensure financial oversight, vendor compliance, and alignment with IT standards.</p>
                        <ol className="approval-steps">
                            {approvalSteps.map(([title, text]) => (
                                <li key={title}>
                                    <strong>{title}:</strong> {text}
                                </li>
                            ))}
                        </ol>
                        <CitationList expanded={expanded} onToggle={onToggleExpanded} onOpenDetail={onOpenDetail} />
                    </>
                ) : (
                    <div className="search-results">{filteredDocuments.length ? filteredDocuments.map((document) => <DocumentRow key={document.title} document={document} saved={saved.includes(document.title)} onOpen={onOpenDetail} onToggleSaved={onToggleSaved} />) : <p>No sample documents match your search.</p>}</div>
                )}
            </div>
            {mode === "Synthesis" && (
                <footer className="answer-footer">
                    <span>{feedback ? "Thanks — feedback recorded for this session." : "Was this answer helpful?"}</span>
                    <button className="icon-button" aria-label="Helpful" aria-pressed={feedback === "yes"} onClick={() => onFeedback("yes")}>
                        <Icon name="up" size={17} />
                    </button>
                    <button className="icon-button" aria-label="Not helpful" aria-pressed={feedback === "no"} onClick={() => onFeedback("no")}>
                        <Icon name="down" size={17} />
                    </button>
                    <button className="copy-button" onClick={copyAnswer}>
                        Copy <Icon name="copy" size={18} />
                    </button>
                </footer>
            )}
        </section>
    );
}

import type { FormEvent } from "react";
import { Icon } from "../../components/ui/Icon";
import type { AskAiMode } from "./askAiApi";

interface QuestionFormProps {
    question: string;
    mode: AskAiMode;
    submitting: boolean;
    canceled: boolean;
    onQuestionChange: (value: string) => void;
    onModeChange: (value: AskAiMode) => void;
    onSubmit: (event: FormEvent) => void;
    onCancel: () => void;
}

export function QuestionForm({ question, mode, submitting, canceled, onQuestionChange, onModeChange, onSubmit, onCancel }: QuestionFormProps) {
    return (
        <form className="question-panel panel" onSubmit={onSubmit}>
            <label htmlFor="question">Your question</label>
            <div className="question-input">
                <textarea
                    id="question"
                    value={question}
                    placeholder="Ask about indexed knowledge…"
                    aria-describedby="question-keyboard-help"
                    onChange={(event) => onQuestionChange(event.target.value)}
                    onKeyDown={(event) => {
                        // Composition confirmation must not send a partially entered question.
                        if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) return;
                        event.preventDefault();
                        if (!submitting && question.trim() && !event.repeat) event.currentTarget.form?.requestSubmit();
                    }}
                />
                <button className="send-button" aria-label="Submit question" disabled={submitting || !question.trim()}>
                    <Icon name="send" size={18} />
                </button>
            </div>
            <p id="question-keyboard-help" className="question-keyboard-help">
                Enter to submit · Shift+Enter for a new line
            </p>
            <fieldset>
                <legend>
                    Mode <span title="Search and synthesis will use live knowledge when connected.">ⓘ</span>
                </legend>
                <div className="mode-options">
                    {(["Search", "Synthesis"] as const).map((value) => (
                        <label className={`mode-option ${mode === value ? "selected" : ""}`} key={value}>
                            <Icon name={value === "Search" ? "search" : "spark"} size={18} />
                            <span>{value}</span>
                            <input type="radio" name="mode" value={value} checked={mode === value} disabled={submitting} onChange={() => onModeChange(value)} />
                        </label>
                    ))}
                </div>
            </fieldset>
            {(submitting || canceled) && (
                <div className="query-request-status">
                    <p role="status">{submitting ? "Request in progress. Cancel stops waiting; server processing may continue." : "Request canceled. Server processing may continue. You can submit another question."}</p>
                    {submitting && (
                        <button className="secondary-button" type="button" onClick={onCancel}>
                            Cancel request
                        </button>
                    )}
                </div>
            )}
        </form>
    );
}

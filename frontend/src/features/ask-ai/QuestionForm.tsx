import type { FormEvent } from "react";
import { Icon } from "../../components/ui/Icon";
import type { AskAiMode } from "./askAiApi";
import { DEFAULT_SEARCH_LIMIT, normalizeQuestion, questionError, validSearchLimit } from "./queryInput";

interface QuestionFormProps {
    question: string;
    mode: AskAiMode;
    limit?: number;
    submitting: boolean;
    canceled: boolean;
    onQuestionChange: (value: string) => void;
    onModeChange: (value: AskAiMode) => void;
    onLimitChange?: (value: number) => void;
    onSubmit: (event: FormEvent) => void;
    onCancel: () => void;
}

export function QuestionForm({ question, mode, limit = DEFAULT_SEARCH_LIMIT, submitting, canceled, onQuestionChange, onModeChange, onLimitChange, onSubmit, onCancel }: QuestionFormProps) {
    const validationError = (question ? questionError(mode, question) : null) ?? (mode === "Search" && !validSearchLimit(limit) ? "Search result limit must be an integer from 1 to 50." : null);
    const canSubmit = !submitting && Boolean(normalizeQuestion(question)) && !validationError;
    const resizeTextarea = (element: HTMLTextAreaElement | null) => {
        if (!element) return;
        element.style.height = "auto";
        element.style.height = `${Math.min(element.scrollHeight, 160)}px`;
    };

    return (
        <form className="question-panel panel chat-composer" onSubmit={onSubmit}>
            <label htmlFor="question">Message KnowledgeBridge</label>
            <div className="question-input">
                <textarea
                    id="question"
                    ref={resizeTextarea}
                    value={question}
                    placeholder="Ask about indexed knowledge…"
                    aria-describedby={validationError ? "question-keyboard-help question-validation-error" : "question-keyboard-help"}
                    onChange={(event) => {
                        resizeTextarea(event.currentTarget);
                        onQuestionChange(event.target.value);
                    }}
                    onKeyDown={(event) => {
                        // Composition confirmation must not send a partially entered question.
                        if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) return;
                        event.preventDefault();
                        if (canSubmit && !event.repeat) event.currentTarget.form?.requestSubmit();
                    }}
                />
                <button className="send-button" aria-label="Submit question" disabled={!canSubmit}>
                    <Icon name="send" size={18} />
                </button>
            </div>
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
            {mode === "Search" && (
                <label>
                    Result limit (1–50)
                    <input
                        aria-label="Result limit"
                        aria-invalid={!validSearchLimit(limit)}
                        aria-describedby={!validSearchLimit(limit) ? "question-validation-error" : undefined}
                        type="number"
                        min={1}
                        max={50}
                        step={1}
                        value={limit}
                        disabled={submitting}
                        onChange={(event) => onLimitChange?.(Number(event.target.value))}
                    />
                </label>
            )}
            {validationError && (
                <p id="question-validation-error" className="query-error" role="alert">
                    {validationError}
                </p>
            )}
            <p id="question-keyboard-help" className="question-keyboard-help">
                Enter to send · Shift+Enter for a new line
            </p>
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

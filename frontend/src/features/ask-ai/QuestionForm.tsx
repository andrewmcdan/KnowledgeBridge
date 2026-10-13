import type { FormEvent } from "react";
import { Icon } from "../../components/ui/Icon";
import type { AskAiMode } from "./askAiApi";

interface QuestionFormProps {
    question: string;
    mode: AskAiMode;
    submitting: boolean;
    onQuestionChange: (value: string) => void;
    onModeChange: (value: AskAiMode) => void;
    onSubmit: (event: FormEvent) => void;
}

export function QuestionForm({ question, mode, submitting, onQuestionChange, onModeChange, onSubmit }: QuestionFormProps) {
    return (
        <form className="question-panel panel" onSubmit={onSubmit}>
            <label htmlFor="question">Your question</label>
            <div className="question-input">
                <textarea id="question" value={question} placeholder="Ask about indexed knowledge…" onChange={(event) => onQuestionChange(event.target.value)} />
                <button className="send-button" aria-label="Submit question" disabled={submitting || !question.trim()}>
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
        </form>
    );
}

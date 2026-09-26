import type { FormEvent } from "react";
import { Icon } from "../../components/ui/Icon";

interface QuestionFormProps {
    question: string; mode: string; notice: string;
    onQuestionChange: (value: string) => void; onModeChange: (value: string) => void;
    onSubmit: (event: FormEvent) => void;
}

export function QuestionForm({ question, mode, notice, onQuestionChange, onModeChange, onSubmit }: QuestionFormProps) {
    return <form className="question-panel panel" onSubmit={onSubmit}><label htmlFor="question">Your question</label><div className="question-input"><textarea id="question" value={question} onChange={event => onQuestionChange(event.target.value)} /><button className="send-button" aria-label="Submit question" disabled={!question.trim()}><Icon name="send" size={18} /></button></div><fieldset><legend>Mode <span title="Search returns documents; Synthesis combines them into an answer.">ⓘ</span></legend><div className="mode-options">{["Search", "Synthesis"].map(value => <label className={`mode-option ${mode === value ? "selected" : ""}`} key={value}><Icon name={value === "Search" ? "search" : "spark"} size={18} /><span>{value}</span><input type="radio" name="mode" value={value} checked={mode === value} onChange={() => onModeChange(value)} /></label>)}</div></fieldset>{notice && <p className="notice" role="status">{notice}</p>}</form>;
}

import { useState, type FormEvent } from "react";
import { documents, sampleQuestion } from "../../data/demoData";
import type { Detail } from "../../types/models";
import { AnswerPanel } from "./AnswerPanel";
import { QuestionForm } from "./QuestionForm";

export function AskAiPage({ search, saved, onOpenDetail, onToggleSaved }: { search: string; saved: string[]; onOpenDetail: (detail: Detail) => void; onToggleSaved: (title: string) => void }) {
    const [mode, setMode] = useState("Synthesis"); const [question, setQuestion] = useState(sampleQuestion); const [notice, setNotice] = useState(""); const [expanded, setExpanded] = useState(false); const [feedback, setFeedback] = useState("");
    const filteredDocuments = documents.filter(document => `${document.title} ${document.type} ${document.text}`.toLowerCase().includes(search.toLowerCase()));
    const submit = (event: FormEvent) => { event.preventDefault(); setNotice(question.trim() === sampleQuestion ? "Showing the sample response below. Live AI queries are not connected yet." : "Live AI queries are not connected yet. The answer below is the labeled sample, not a response to your question."); };
    return <><QuestionForm question={question} mode={mode} notice={notice} onQuestionChange={setQuestion} onModeChange={setMode} onSubmit={submit} /><AnswerPanel mode={mode} filteredDocuments={filteredDocuments} expanded={expanded} feedback={feedback} saved={saved} onToggleExpanded={() => setExpanded(value => !value)} onFeedback={setFeedback} onOpenDetail={onOpenDetail} onToggleSaved={onToggleSaved} onNotice={setNotice} /></>;
}

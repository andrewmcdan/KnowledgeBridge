import { useState, type FormEvent } from "react";
import { AnswerPanel } from "./AnswerPanel";
import { QuestionForm } from "./QuestionForm";
import { submitQuestion, type AskAiMode, type QueryResult } from "./askAiApi";
import { UsageTotalsPanel } from "../usage/UsagePage";

export function AskAiPage() {
    const [mode, setMode] = useState<AskAiMode>("Synthesis");
    const [question, setQuestion] = useState("");
    const [result, setResult] = useState<QueryResult | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [completedQueries, setCompletedQueries] = useState(0);

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        setSubmitting(true);
        setError(null);
        setResult(null);
        try {
            setResult(await submitQuestion(mode, question.trim()));
            setCompletedQueries((value) => value + 1);
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "We could not complete your question.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <>
            <QuestionForm question={question} mode={mode} submitting={submitting} onQuestionChange={setQuestion} onModeChange={setMode} onSubmit={submit} />
            <AnswerPanel mode={mode} result={result} submitting={submitting} error={error} />
            <UsageTotalsPanel refreshKey={completedQueries} />
        </>
    );
}

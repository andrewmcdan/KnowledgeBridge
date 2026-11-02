import { useEffect, useRef, useState, type FormEvent } from "react";
import { AnswerPanel } from "./AnswerPanel";
import { QuestionForm } from "./QuestionForm";
import { submitQuestion, type AskAiMode, type QueryResult } from "./askAiApi";
import { UsageTotalsPanel } from "../usage/UsagePage";
import type { Detail } from "../../types/models";

export function AskAiPage({ onOpenDetail }: { onOpenDetail: (detail: Detail) => void }) {
    const [mode, setMode] = useState<AskAiMode>("Synthesis");
    const [question, setQuestion] = useState("");
    const [result, setResult] = useState<QueryResult | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [canceled, setCanceled] = useState(false);
    const [completedQueries, setCompletedQueries] = useState(0);
    const activeRequest = useRef<AbortController | null>(null);

    useEffect(
        () => () => {
            const controller = activeRequest.current;
            activeRequest.current = null;
            controller?.abort();
        },
        [],
    );

    const cancel = () => {
        const controller = activeRequest.current;
        if (!controller) return;
        activeRequest.current = null;
        controller.abort();
        setSubmitting(false);
        setCanceled(true);
    };

    const submit = async (event: FormEvent) => {
        event.preventDefault();
        // React state may not have rendered before another submit event arrives.
        if (activeRequest.current || !question.trim()) return;
        const controller = new AbortController();
        activeRequest.current = controller;
        setSubmitting(true);
        setCanceled(false);
        setError(null);
        setResult(null);
        try {
            const response = await submitQuestion(mode, question.trim(), controller.signal);
            if (activeRequest.current !== controller || controller.signal.aborted) return;
            setResult(response);
            setCompletedQueries((value) => value + 1);
        } catch (reason: unknown) {
            if (activeRequest.current === controller && !controller.signal.aborted) setError(reason instanceof Error ? reason.message : "We could not complete your question.");
        } finally {
            if (activeRequest.current === controller) {
                activeRequest.current = null;
                setSubmitting(false);
            }
        }
    };

    return (
        <>
            <QuestionForm question={question} mode={mode} submitting={submitting} canceled={canceled} onQuestionChange={setQuestion} onModeChange={setMode} onSubmit={submit} onCancel={cancel} />
            <AnswerPanel mode={mode} result={result} submitting={submitting} error={error} onOpenDetail={onOpenDetail} />
            <UsageTotalsPanel refreshKey={completedQueries} />
        </>
    );
}

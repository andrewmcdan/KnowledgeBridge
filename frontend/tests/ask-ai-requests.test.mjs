import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import { runInNewContext } from "node:vm";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";

const packageRequire = createRequire(import.meta.url);

function load(name, overrides = {}, globals = {}) {
    const module = { exports: {} };
    const source = readFileSync(new URL(`../src/features/ask-ai/${name}`, import.meta.url), "utf8");
    runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText, {
        module,
        exports: module.exports,
        AbortController,
        Error,
        ...globals,
        require: (specifier) => overrides[specifier] ?? packageRequire(specifier),
    });
    return module.exports;
}

function pageHarness() {
    const states = [];
    const refs = [];
    const requests = [];
    let cursor = 0;
    let refCursor = 0;
    let cleanup;
    let unmounted = false;
    let writesAfterUnmount = 0;
    function QuestionForm() {}
    function AnswerPanel() {}
    function UsageTotalsPanel() {}
    const { AskAiPage } = load("AskAiPage.tsx", {
        react: {
            useState: (initial) => {
                const index = cursor++;
                if (!(index in states)) states[index] = initial;
                return [
                    states[index],
                    (value) => {
                        if (unmounted) writesAfterUnmount++;
                        states[index] = typeof value === "function" ? value(states[index]) : value;
                    },
                ];
            },
            useRef: (initial) => refs[refCursor++] ?? (refs[refCursor - 1] = { current: initial }),
            useEffect: (effect) => {
                cleanup ??= effect();
            },
        },
        "./QuestionForm": { QuestionForm },
        "./AnswerPanel": { AnswerPanel },
        "../usage/UsagePage": { UsageTotalsPanel },
        "./askAiApi": { submitQuestion: (mode, question, signal) => new Promise((resolve, reject) => requests.push({ mode, question, signal, resolve, reject })) },
    });
    const render = () => {
        cursor = 0;
        refCursor = 0;
        const elements = AskAiPage({ onOpenDetail() {} }).props.children;
        return {
            form: elements.find((node) => node.type === QuestionForm).props,
            answer: elements.find((node) => node.type === AnswerPanel).props,
            usage: elements.find((node) => node.type === UsageTotalsPanel).props,
        };
    };
    const page = {
        requests,
        render,
        unmount: () => {
            unmounted = true;
            cleanup();
        },
        writesAfterUnmount: () => writesAfterUnmount,
    };
    render().form.onQuestionChange("  What is the budget?  ");
    return page;
}

function submit(form) {
    let prevented = false;
    const promise = form.onSubmit({
        preventDefault() {
            prevented = true;
        },
    });
    assert.equal(prevented, true);
    return promise;
}

const answer = (text) => ({ mode: "Synthesis", response: { answer: text } });

test("rapid direct submissions use a synchronous lock before React renders in both modes", async () => {
    for (const mode of ["Synthesis", "Search"]) {
        const page = pageHarness();
        page.render().form.onModeChange(mode);
        const form = page.render().form;
        const first = submit(form);
        await Promise.all([submit(form), submit(form)]);
        assert.equal(page.requests.length, 1);
        assert.equal(page.requests[0].mode, mode);
        assert.equal(page.requests[0].question, "What is the budget?");
        assert.equal(page.requests[0].signal.aborted, false);
        assert.equal(page.render().form.submitting, true);
        page.requests[0].resolve({ mode, response: {} });
        await first;
        assert.equal(page.render().form.submitting, false);
        assert.equal(page.render().usage.refreshKey, 1);
        page.unmount();
    }
});

test("cancel aborts the active fetch, allows a retry, and late success cannot stop the newer request", async () => {
    const page = pageHarness();
    const first = submit(page.render().form);
    page.render().form.onCancel();
    assert.equal(page.requests[0].signal.aborted, true);
    assert.equal(page.render().form.canceled, true);
    assert.equal(page.render().form.submitting, false);
    const second = submit(page.render().form);
    page.requests[0].resolve(answer("Stale answer"));
    await first;
    assert.equal(page.render().answer.result, null);
    assert.equal(page.render().form.submitting, true);
    assert.equal(page.render().form.canceled, false);
    assert.equal(page.render().usage.refreshKey, 0);
    page.requests[1].resolve(answer("Current answer"));
    await second;
    assert.equal(page.render().answer.result.response.answer, "Current answer");
    assert.equal(page.render().usage.refreshKey, 1);
    page.unmount();
});

test("late rejection cannot replace a newer result or display an error after cancellation", async () => {
    const page = pageHarness();
    const first = submit(page.render().form);
    page.render().form.onCancel();
    const second = submit(page.render().form);
    page.requests[1].resolve(answer("Current answer"));
    await second;
    page.requests[0].reject(new Error("Late server failure"));
    await first;
    assert.equal(page.render().answer.result.response.answer, "Current answer");
    assert.equal(page.render().answer.error, null);
    assert.equal(page.render().form.submitting, false);
    assert.equal(page.render().usage.refreshKey, 1);
    page.render().form.onCancel();
    assert.equal(page.render().form.canceled, false, "cancel is harmless when no request is active");
    page.unmount();
});

test("an abort rejection is quiet and ordinary errors release the guard for retry", async () => {
    const page = pageHarness();
    const first = submit(page.render().form);
    page.render().form.onCancel();
    page.requests[0].reject(new DOMException("Aborted", "AbortError"));
    await first;
    assert.equal(page.render().answer.error, null);
    const failed = submit(page.render().form);
    page.requests[1].reject(new Error("Engine unavailable"));
    await failed;
    assert.equal(page.render().answer.error, "Engine unavailable");
    assert.equal(page.render().form.submitting, false);
    const retry = submit(page.render().form);
    assert.equal(page.render().answer.error, null);
    page.requests[2].resolve(answer("Recovered"));
    await retry;
    assert.equal(page.render().answer.result.response.answer, "Recovered");
    assert.equal(page.render().usage.refreshKey, 1);
    page.unmount();
});

test("unmount aborts pending work and all late completions avoid state writes", async () => {
    for (const outcome of ["resolve", "reject"]) {
        const page = pageHarness();
        const request = submit(page.render().form);
        page.unmount();
        assert.equal(page.requests[0].signal.aborted, true);
        page.requests[0][outcome](outcome === "resolve" ? answer("Late answer") : new Error("Late failure"));
        await request;
        assert.equal(page.writesAfterUnmount(), 0);
    }
});

test("blank direct submissions never start a request", async () => {
    const page = pageHarness();
    page.render().form.onQuestionChange("  \n\t");
    await submit(page.render().form);
    assert.equal(page.requests.length, 0);
    assert.equal(page.render().form.submitting, false);
    page.unmount();
});

test("cancellation UI is a separate native button with honest, accessible status", () => {
    const { QuestionForm } = load("QuestionForm.tsx", { "../../components/ui/Icon": { Icon: () => null } });
    const props = { question: "Question", mode: "Search", submitting: true, canceled: false, onQuestionChange() {}, onModeChange() {}, onSubmit() {}, onCancel() {} };
    const busy = renderToStaticMarkup(createElement(QuestionForm, props));
    assert.match(busy, /type="button"[^>]*>Cancel request/);
    assert.match(busy, /role="status"/);
    assert.match(busy, /server processing may continue/);
    const canceled = renderToStaticMarkup(createElement(QuestionForm, { ...props, submitting: false, canceled: true }));
    assert.match(canceled, /Request canceled/);
    assert.doesNotMatch(canceled, /Cancel request|role="alert"/);
});

test("Enter submits the question through the form in both modes without bypassing the in-flight guard", async () => {
    const { QuestionForm } = load("QuestionForm.tsx", { "../../components/ui/Icon": { Icon: () => null } });
    for (const mode of ["Search", "Synthesis"]) {
        const page = pageHarness();
        page.render().form.onModeChange(mode);
        const form = page.render().form;
        const textarea = QuestionForm(form).props.children[1].props.children[0];
        const submissions = [];
        let prevented = 0;
        const event = {
            key: "Enter",
            shiftKey: false,
            repeat: false,
            nativeEvent: { isComposing: false, keyCode: 13 },
            currentTarget: { form: { requestSubmit: () => submissions.push(submit(form)) } },
            preventDefault() {
                prevented++;
            },
        };
        textarea.props.onKeyDown(event);
        textarea.props.onKeyDown(event);
        assert.equal(prevented, 2, "Enter should not insert a newline");
        assert.equal(page.requests.length, 1, "rapid key presses cannot start duplicate API calls");
        assert.equal(page.requests[0].mode, mode);
        assert.equal(page.requests[0].question, "What is the budget?");
        page.requests[0].resolve({ mode, response: {} });
        await Promise.all(submissions);
        assert.equal(page.render().form.submitting, false);
        page.unmount();
    }
});

test("Shift+Enter stays native while blank, busy, repeating, and composing input cannot submit", () => {
    const { QuestionForm } = load("QuestionForm.tsx", { "../../components/ui/Icon": { Icon: () => null } });
    const cases = [
        { key: "Enter", shiftKey: true, prevented: false },
        { key: "a", prevented: false },
        { key: "Enter", isComposing: true, prevented: false },
        { key: "Enter", keyCode: 229, prevented: false },
        { key: "Enter", question: " \n\t", prevented: true },
        { key: "Enter", submitting: true, prevented: true },
        { key: "Enter", repeat: true, prevented: true },
    ];
    for (const mode of ["Search", "Synthesis"]) {
        for (const scenario of cases) {
            let submitted = 0;
            let prevented = false;
            const props = { question: scenario.question ?? "Question", mode, submitting: scenario.submitting ?? false, canceled: false, onQuestionChange() {}, onModeChange() {}, onSubmit() {}, onCancel() {} };
            const textarea = QuestionForm(props).props.children[1].props.children[0];
            textarea.props.onKeyDown({
                key: scenario.key,
                shiftKey: scenario.shiftKey ?? false,
                repeat: scenario.repeat ?? false,
                nativeEvent: { isComposing: scenario.isComposing ?? false, keyCode: scenario.keyCode ?? 13 },
                currentTarget: { form: { requestSubmit: () => submitted++ } },
                preventDefault() {
                    prevented = true;
                },
            });
            assert.equal(submitted, 0, JSON.stringify(scenario));
            assert.equal(prevented, scenario.prevented, JSON.stringify(scenario));
            assert.equal(textarea.props["aria-describedby"], "question-keyboard-help");
        }
    }
});

test("API forwards cancellation, aborts fetch, and does not dispatch a pre-aborted request", async () => {
    const calls = [];
    const { submitQuestion } = load(
        "askAiApi.ts",
        {
            "../../api/apiError": {
                readApiError() {
                    throw new Error("Unexpected error decoding");
                },
            },
        },
        {
            sessionStorage: { getItem: () => "token" },
            fetch: (path, options) =>
                new Promise((_resolve, reject) => {
                    calls.push({ path, options });
                    options.signal.addEventListener("abort", () => reject(options.signal.reason), { once: true });
                }),
        },
    );
    for (const mode of ["Search", "Synthesis"]) {
        const controller = new AbortController();
        const request = submitQuestion(mode, "Question", controller.signal);
        assert.equal(calls.at(-1).options.signal, controller.signal);
        assert.equal(calls.at(-1).path, mode === "Search" ? "/api/query/search" : "/api/query/synthesize");
        controller.abort();
        await assert.rejects(request, { name: "AbortError" });
        await assert.rejects(submitQuestion(mode, "Question", controller.signal), { name: "AbortError" });
    }
    assert.equal(calls.length, 2);
});

test("API ignores a response that arrives after cancellation even if transport ignores abort", async () => {
    for (const phase of ["headers", "body"]) {
        const controller = new AbortController();
        const { submitQuestion } = load(
            "askAiApi.ts",
            {
                "../../api/apiError": {
                    readApiError() {
                        throw new Error("Canceled responses must not expire the session");
                    },
                },
            },
            {
                sessionStorage: { getItem: () => "token" },
                fetch: async () => {
                    if (phase === "headers") controller.abort();
                    return {
                        ok: phase === "body",
                        status: phase === "body" ? 200 : 401,
                        json: async () => {
                            controller.abort();
                            return { hits: [], retrieval: { vectorEnabled: true, projectionReady: true, degraded: false } };
                        },
                    };
                },
            },
        );
        await assert.rejects(submitQuestion("Search", "Question", controller.signal), { name: "AbortError" });
    }
});

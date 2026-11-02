import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import { runInNewContext } from "node:vm";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import ts from "typescript";

const packageRequire = createRequire(import.meta.url);
function load(name, overrides = {}) {
    const module = { exports: {} };
    const source = readFileSync(new URL(`../src/components/ui/${name}`, import.meta.url), "utf8");
    runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText, {
        module,
        exports: module.exports,
        require: (specifier) => {
            if (specifier in overrides) return overrides[specifier];
            if (specifier === "react-markdown") return { __esModule: true, default: Markdown };
            if (specifier === "remark-gfm") return { __esModule: true, default: remarkGfm };
            if (specifier.startsWith("./")) return load(`${specifier.slice(2)}.ts`, overrides);
            return packageRequire(specifier);
        },
    });
    return module.exports;
}
const { matchReferencePassages, rankReferencePassages } = load("referencePassages.ts");
const { citedPassages } = load("../../features/ask-ai/citations.ts");
const { MarkdownContent } = load("MarkdownContent.tsx");
const { parseDocumentMarkdown } = load("documentPreview.ts");
const plain = (value) => JSON.parse(JSON.stringify(value));

test("search excerpts match formatted text, truncated passages, Unicode and normalized amounts", () => {
    const paragraphs = ["A remote employee handbook.", "Finance requires manager approval for annual software costs above $20,000.", "Café equipment requires Finance review before purchase."];
    const focus = { kind: "search", passages: ["requires **manager approval** for annual software costs above $20000", "CAFE\u0301 equipment\nrequires Finance review"], question: "Who approves software?" };
    assert.deepEqual(plain(matchReferencePassages(paragraphs, focus)), [1, 2]);
    assert.equal(paragraphs[1], "Finance requires manager approval for annual software costs above $20,000.");
});

test("paraphrased synthesis finds related passages without treating generic words as evidence", () => {
    const paragraphs = ["Managers approve travel requests before booking.", "The meal allowance is $65 per person per travel day, including taxes and tips.", "Production access requires SSO and MFA."];
    assert.deepEqual(plain(matchReferencePassages(paragraphs, { kind: "synthesis", passages: ["Meals have a $65 allowance per travel day and include taxes and tips."], question: "Travel meal allowance" })), [1]);
    for (const passages of [[], ["the current policy in this document"], ["No approved European hosting region has been selected."]]) {
        assert.deepEqual(plain(matchReferencePassages(paragraphs, { kind: "synthesis", passages })), []);
    }
});

test("highlights are bounded, deterministic, ordered and prioritize the submitted question", () => {
    const paragraphs = Array.from({ length: 10 }, (_, i) => `Annual software approval requires Finance review for Project ${i === 9 ? "Cedar" : `Example${i}`}.`);
    const focus = { kind: "search", passages: paragraphs, question: "Project Cedar annual software approval" };
    const selected = plain(matchReferencePassages(paragraphs, focus));
    assert.equal(selected.length, 6);
    assert.ok(selected.includes(9));
    assert.deepEqual(
        selected,
        [...selected].sort((a, b) => a - b),
    );
    assert.deepEqual(plain(matchReferencePassages(paragraphs, focus)), selected);
    assert.equal(rankReferencePassages(paragraphs, focus)[0], 9);
});

test("section headings prioritize a relevant worked example without manufacturing a passage match", () => {
    const passages = ["Manager and Finance approve annual software commitments.", "The proposed renewal needs manager and Finance approval.", "No relevant evidence exists in this passage."];
    const focus = { kind: "search", question: "Nimbus Compute renewal", passages: ["Manager and Finance approve annual software commitments. The proposed renewal needs manager and Finance approval."] };
    assert.equal(rankReferencePassages(passages, focus, ["Approval thresholds", "Worked example: Nimbus Compute", "Nimbus Compute renewal"])[0], 1);
    assert.ok(!rankReferencePassages(passages, focus, ["", "", "Nimbus Compute renewal"]).includes(2));
});

test("synthesis context follows exact citation markers and excludes code and other sources", () => {
    const answer =
        "Finance reviews annual spending [1].\n\nProduction access requires MFA [10].\n\n```text\nNot a cited claim [1].\n```\n\n`not evidence [1]`\n\nThe source is [knowledgebridge/21].\n\n[Policy](knowledgebridge/21) sets the thresholds.";
    assert.deepEqual(plain(citedPassages(answer, 1, "knowledgebridge/21")), ["Finance reviews annual spending [1].", "The source is [knowledgebridge/21].", "[Policy](knowledgebridge/21) sets the thresholds."]);
    assert.deepEqual(plain(citedPassages(answer, 2, "knowledgebridge/99")), []);
    assert.deepEqual(plain(citedPassages("``literal [1]``\n\n[Other source](knowledgebridge/99)", 1, "knowledgebridge/21")), []);
    assert.deepEqual(plain(citedPassages("A fallback without inline markers.", 1, "knowledgebridge/21")), ["A fallback without inline markers."]);
});

test("Markdown passage highlighting preserves formatting, lists, tables, code and safe rendering", () => {
    const text =
        "# Controls\n\nFinance requires **manager approval** before software purchase.\n\n- The manager checks annual software costs.\n\n| Cost | Review |\n| --- | --- |\n| Above $20,000 | Manager, Finance and CFO approval |\n\n```text\nannual software approval status\n```\n\n<script>bad()</script>\n\n[Unsafe](javascript:alert%281%29)";
    const referenceFocus = {
        kind: "search",
        passages: ["Finance requires manager approval before software purchase.", "The manager checks annual software costs.", "Above $20,000 Manager, Finance and CFO approval", "annual software approval status"],
    };
    const html = renderToStaticMarkup(createElement(MarkdownContent, { text, referenceFocus, activePassage: 2 }));
    assert.equal((html.match(/data-reference-passage=/g) ?? []).length, 4);
    assert.match(html, /<strong>manager approval<\/strong>/);
    assert.match(html, /<li class="document-passage-match/);
    assert.match(html, /<tr class="document-passage-match document-passage-active" data-reference-passage="2"/);
    assert.match(html, /<pre class="document-passage-match/);
    assert.match(html, /<code class="language-text">/);
    assert.doesNotMatch(html, /<script|javascript:|<h1 class="document-passage/);
    const normal = renderToStaticMarkup(createElement(MarkdownContent, { text }));
    assert.doesNotMatch(normal, /data-reference-passage|document-passage-match/);
});

test("metadata is excluded and an unmatched or changed document stays fully readable", () => {
    const parsed = parseDocumentMarkdown("---\nowner: Finance approval thresholds\n---\n# Revised policy\n\nThe company has not approved a European hosting region.");
    const html = renderToStaticMarkup(createElement(MarkdownContent, { text: parsed.markdown, referenceFocus: { kind: "search", passages: ["Finance approval thresholds"] } }));
    assert.doesNotMatch(html, /data-reference-passage|owner:/);
    assert.match(html, /The company has not approved a European hosting region/);
});

function find(node, predicate) {
    if (!node || typeof node !== "object") return null;
    if (Array.isArray(node)) return node.map((child) => find(child, predicate)).find(Boolean) ?? null;
    return predicate(node) ? node : find(node.props?.children, predicate);
}

function navigationHarness(total, primary = 0) {
    const states = [];
    const effects = [];
    const pending = [];
    let cursor = 0;
    let effectCursor = 0;
    const ref = { current: null };
    const scrolls = [];
    const focus = { kind: "synthesis", passages: ["Annual software spending approval"] };
    const elements = Array.from({ length: total }, (_, i) => ({ scrollIntoView: (options) => scrolls.push({ i, options: plain(options) }) }));
    function MarkdownContent() {}
    const { DocumentPassages } = load("DocumentPassages.tsx", {
        react: {
            useRef: () => ref,
            useCallback: (callback) => callback,
            useState: (initial) => {
                const i = cursor++;
                if (!(i in states)) states[i] = initial;
                return [
                    states[i],
                    (value) => {
                        states[i] = typeof value === "function" ? value(states[i]) : value;
                    },
                ];
            },
            useEffect: (effect, deps) => {
                const i = effectCursor++;
                if (!effects[i] || deps.some((dep, n) => effects[i][n] !== dep)) {
                    effects[i] = deps;
                    pending.push(effect);
                }
            },
        },
        "./MarkdownContent": { MarkdownContent },
    });
    const render = () => {
        cursor = 0;
        effectCursor = 0;
        return DocumentPassages({ text: "Source text", focus });
    };
    const first = render();
    first.props.ref({ querySelectorAll: () => elements, querySelector: () => ({ getAttribute: () => String(primary) }) });
    const flush = () => pending.splice(0).forEach((effect) => effect());
    flush();
    return {
        render: () => {
            const tree = render();
            flush();
            return tree;
        },
        scrolls,
    };
}

test("modal passage navigation scrolls on mount, changes the active highlight and wraps in both directions", () => {
    const page = navigationHarness(3);
    let tree = page.render();
    assert.equal(page.scrolls[0].i, 0);
    assert.deepEqual(page.scrolls[0].options, { block: "center", inline: "nearest", behavior: "instant" });
    assert.equal(find(tree, (node) => node.props?.role === "status").props.children, "Passage 1 of 3");
    find(tree, (node) => node.type === "button" && node.props.children === "Next passage").props.onClick();
    tree = page.render();
    assert.equal(page.scrolls.at(-1).i, 1);
    assert.equal(find(tree, (node) => node.type?.name === "MarkdownContent").props.activePassage, 1);
    find(tree, (node) => node.type === "button" && node.props.children === "Previous passage").props.onClick();
    tree = page.render();
    find(tree, (node) => node.type === "button" && node.props.children === "Previous passage").props.onClick();
    tree = page.render();
    assert.equal(page.scrolls.at(-1).i, 2);
    assert.equal(find(tree, (node) => node.props?.role === "status").props.children, "Passage 3 of 3");
});

test("no-match navigation reports the outcome without scrolling, and a single match disables cycling", () => {
    const empty = navigationHarness(0);
    const tree = empty.render();
    assert.equal(empty.scrolls.length, 0);
    assert.match(find(tree, (node) => node.props?.role === "status").props.children, /No matching passage/);
    assert.equal(
        find(tree, (node) => node.type === "button"),
        null,
    );
    assert.equal(find(navigationHarness(1).render(), (node) => node.type === "button").props.disabled, true);
    const best = navigationHarness(3, 2);
    assert.equal(find(best.render(), (node) => node.props?.role === "status").props.children, "Passage 3 of 3");
    assert.equal(best.scrolls.at(-1).i, 2);
});

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
        Map,
        Error,
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

const { parseDocumentMarkdown, groupDocumentEntities } = load("documentPreview.ts");
const { MarkdownContent } = load("MarkdownContent.tsx");
const { citationDetail } = load("../../features/ask-ai/citations.ts");
const { AnswerPanel } = load("../../features/ask-ai/AnswerPanel.tsx", {
    "../../components/ui/Icon": { Icon: () => null },
    "../../components/ui/MarkdownContent": { MarkdownContent },
    "../../components/ui/documentPreview": { parseDocumentMarkdown },
    "./citations": { citationDetail },
});
const { DocumentEntitiesPanel } = load("DocumentEntitiesPanel.tsx");
const plain = (value) => JSON.parse(JSON.stringify(value));

test("front matter becomes metadata while Markdown and plain text remain readable", () => {
    const parsed = parseDocumentMarkdown('\uFEFF---\r\ntitle: "Budget: review"\r\nrelated_people: [Priya Shah, Leo Martin]\r\nowner:\r\n  team: Finance\r\n---\r\n# Budget\r\n\r\nA **clear** decision.');
    assert.equal(parsed.markdown, "# Budget\n\nA **clear** decision.");
    assert.deepEqual(plain(parsed.metadata).slice(0, 2), [
        { label: "title", value: "Budget: review" },
        { label: "related people", value: "Priya Shah, Leo Martin" },
    ]);
    assert.match(parsed.metadata[2].value, /Finance/);
    assert.equal(parsed.metadataError, null);
    assert.equal(parseDocumentMarkdown("Plain text.").markdown, "Plain text.");
    assert.equal(parseDocumentMarkdown("---\nUnclosed front matter").rawMetadata, null);
});

test("malformed, aliased and oversized metadata is retained without hiding the document body", () => {
    for (const source of ["title: [broken", "a: &ref one\nb: *ref", "a: " + "x".repeat(64_001)]) {
        const parsed = parseDocumentMarkdown(`---\n${source}\n---\n# Still readable`);
        assert.equal(parsed.markdown, "# Still readable");
        assert.equal(parsed.rawMetadata, source);
        assert.match(parsed.metadataError, /original metadata/);
    }
    assert.deepEqual(plain(parseDocumentMarkdown("---\njust a value\n---\nBody").metadata), [{ label: "Value", value: "just a value" }]);
});

test("Markdown renders headings, emphasis, tables, task lists, quotes, code and safe links", () => {
    const html = renderToStaticMarkup(
        createElement(MarkdownContent, {
            text: "# Budget review\n\nA **clear** and *readable* plan.\n\n| Team | Budget |\n| --- | --- |\n| Finance | $5,000 |\n\n- [x] Approved\n- [ ] Pending\n\n> Keep the context.\n\n```js\nconst approved = true;\n```\n\n[Policy](https://example.test/policy)",
        }),
    );
    for (const tag of ["<h1>", "<strong>", "<em>", "<table>", "<blockquote>", "<pre>", "<code"]) assert.ok(html.includes(tag), tag);
    assert.match(html, /type="checkbox"/);
    assert.match(html, /checked=""/);
    assert.match(html, /target="_blank" rel="noopener noreferrer"/);
});

test("unsafe HTML and links are blocked, and remote images are represented as links", () => {
    const html = renderToStaticMarkup(createElement(MarkdownContent, { text: '<script>alert("private")</script>\n\n[Unsafe](javascript:alert%281%29)\n\n![Chart](https://example.test/tracker.png)\n\n<img src=x onerror=alert(1)>' }));
    assert.doesNotMatch(html, /<script|javascript:|<img|onerror=/);
    assert.match(html, /Image: Chart/);
    assert.match(html, /href="https:\/\/example.test\/tracker.png"/);
});

test("AI answers render Markdown while retaining citations, warnings and query usage", () => {
    const response = {
        answer: "## Budget review\n\nA **clear** plan with citation [1].\n\n| Owner | Decision |\n| --- | --- |\n| Finance | Approve |\n\n- Share the forecast\n\n```sql\nSELECT budget FROM plan;\n```",
        aiGenerated: true,
        extractiveFallback: false,
        cacheHit: false,
        citations: [{ externalId: "test-1", itemKey: "test-1", title: "Budget policy", documentType: "Policy" }],
        gaps: ["Final approval pending."],
        warnings: ["Verify dates."],
        usage: { inputTokens: 120, outputTokens: 80, estimated: true },
    };
    const render = (overrides = {}) => renderToStaticMarkup(createElement(AnswerPanel, { mode: "Synthesis", result: { mode: "Synthesis", response: { ...response, ...overrides } }, submitting: false, error: null, onOpenDetail() {} }));
    const html = render();
    assert.match(html, /class="markdown-content synthesis-answer" aria-label="AI answer"/);
    assert.match(html, /<h2>Budget review<\/h2>/);
    assert.match(html, /<strong>clear<\/strong>/);
    assert.match(html, /<table>/);
    assert.match(html, /<li>Share the forecast<\/li>/);
    assert.match(html, /<pre><code class="language-sql">/);
    assert.match(html, /citation \[1\]/);
    assert.match(html, /Budget policy/);
    assert.match(html, /Citations \(1\)/);
    assert.match(html, /Final approval pending\./);
    assert.match(html, /Verify dates\./);
    assert.match(html, /Estimated.*120 input \+ 80 output = 200 total/s);
    assert.doesNotMatch(html, /## Budget review|\*\*clear\*\*/);
    assert.match(render({ cacheHit: true }), /Cached answer · 0 new synthesis tokens/);
    assert.match(render({ aiGenerated: false, extractiveFallback: true }), /Extractive fallback/);
    const unsafe = render({ answer: '<script>alert("x")</script>\n\n[Bad](javascript:alert%281%29)' });
    assert.doesNotMatch(unsafe, /<script|javascript:/);
});

test("search results render safe Markdown excerpts without front matter and retain engine ranking", () => {
    const hits = [
        {
            itemKey: "21",
            externalId: "knowledgebridge/21",
            title: "Budget policy",
            documentType: "policy",
            score: 0.8,
            snippet: "---\nowner: Hidden metadata\n---\n# Budget\n\nA **clear** plan.\n\n| Team | Budget |\n| --- | --- |\n| Finance | $5,000 |\n\n- Review spending\n\n<script>alert(1)</script>\n\n[Unsafe](javascript:alert%281%29)",
        },
        { itemKey: "22", externalId: "knowledgebridge/22", title: "Budget meeting", documentType: "video-meeting", score: 0.9, snippet: "A plain text transcript." },
    ];
    const props = { mode: "Search", result: { mode: "Search", response: { hits, retrieval: { degraded: false } } }, submitting: false, error: null, onOpenDetail() {} };
    const html = renderToStaticMarkup(createElement(AnswerPanel, props));
    assert.match(html, /2 results · Ranked by relevance/);
    assert.match(html, /<h1>Budget<\/h1>/);
    assert.match(html, /<strong>clear<\/strong>/);
    assert.match(html, /<table>/);
    assert.match(html, /<li>Review spending<\/li>/);
    assert.match(html, /video meeting/);
    assert.match(html, /A plain text transcript\./);
    assert.match(html, /role="region" aria-label="Matching excerpt: Budget policy" tabindex="0"/);
    assert.doesNotMatch(html, /Hidden metadata|<script|javascript:|\*\*clear\*\*/);
    assert.ok(html.indexOf("<h3>Budget policy</h3>") < html.indexOf("<h3>Budget meeting</h3>"));
    const empty = renderToStaticMarkup(createElement(AnswerPanel, { ...props, result: { mode: "Search", response: { hits: [], retrieval: { degraded: true } } } }));
    assert.match(empty, /No indexed knowledge matched/);
    assert.match(empty, /Some semantic retrieval signals were unavailable/);
});

test("View document selects the exact source ID for the full-document modal and handles unavailable sources", () => {
    const opened = [];
    const hits = [
        { itemKey: "21", externalId: "knowledgebridge/21", title: "Budget policy", documentType: "policy", score: 0.8, snippet: "A partial excerpt." },
        { itemKey: "invalid", externalId: "knowledgebridge/22", title: "Unavailable source", documentType: "", score: 0.7, snippet: "---\nowner: Finance\n---" },
    ];
    const props = { mode: "Search", result: { mode: "Search", response: { hits, retrieval: { degraded: false } } }, submitting: false, error: null, onOpenDetail: (detail) => opened.push(detail) };
    const html = renderToStaticMarkup(createElement(AnswerPanel, props));
    assert.equal((html.match(/aria-label="View document:/g) ?? []).length, 1);
    assert.match(html, /No text excerpt available/);
    assert.match(html, /Document preview unavailable/);
    const find = (node, predicate) => {
        if (!node || typeof node !== "object") return null;
        if (Array.isArray(node)) return node.map((child) => find(child, predicate)).find(Boolean) ?? null;
        return predicate(node) ? node : find(node.props?.children, predicate);
    };
    const results = find(AnswerPanel(props), (node) => node.type?.name === "SearchResults");
    const tree = results.type(results.props);
    find(tree, (node) => node.props?.["aria-label"] === "View document: Budget policy").props.onClick();
    assert.deepEqual(plain(opened), [{ id: 21, title: "Budget policy", text: "" }], "the modal must fetch the source, not display the partial search excerpt");
});

test("citations select only exact application document IDs", () => {
    const citation = { itemKey: "21", externalId: "knowledgebridge/21", title: "Budget policy", documentType: "Policy" };
    assert.deepEqual(plain(citationDetail(citation)), { id: 21, title: "Budget policy", text: "" });
    assert.equal(citationDetail({ ...citation, title: null }).title, "Document 21");
    for (const itemKey of ["", "0", "-1", "1.5", "01", " 21", "21x", "knowledgebridge/21", "9007199254740992"]) {
        assert.equal(citationDetail({ ...citation, itemKey }), null, itemKey);
    }
});

test("inline citation markers open the same source as the citation list, without changing code or unrelated links", () => {
    const opened = [];
    const citation = { itemKey: "21", externalId: "knowledgebridge/21", title: "Budget policy", documentType: "Policy" };
    const response = {
        answer: "A decision [1] from [knowledgebridge/21]. Unknown [9] and [knowledgebridge/999].\n\n`[1]`\n\n```text\n[knowledgebridge/21]\n```\n\n[Source](knowledgebridge/21) and [Example](https://example.test/policy)",
        aiGenerated: true,
        extractiveFallback: false,
        cacheHit: true,
        citations: [citation],
        gaps: [],
        warnings: [],
        usage: null,
    };
    const props = { mode: "Synthesis", result: { mode: "Synthesis", response }, submitting: false, error: null, onOpenDetail: (detail) => opened.push(detail) };
    const html = renderToStaticMarkup(createElement(AnswerPanel, props));
    assert.equal((html.match(/aria-label="Open citation 1: Budget policy"/g) ?? []).length, 3);
    assert.match(html, /aria-label="Open document: Budget policy"/);
    assert.match(html, /Unknown \[9\] and \[knowledgebridge\/999\]/);
    assert.match(html, /<code>\[1\]<\/code>/);
    assert.match(html, /<code class="language-text">\[knowledgebridge\/21\]/);
    assert.match(html, /href="https:\/\/example.test\/policy" target="_blank"/);
    assert.doesNotMatch(html, /<a[^>]+href="knowledgebridge\/21"/);
    const find = (node, predicate) => {
        if (!node || typeof node !== "object") return null;
        if (Array.isArray(node)) return node.map((child) => find(child, predicate)).find(Boolean) ?? null;
        return predicate(node) ? node : find(node.props?.children, predicate);
    };
    const synthesis = find(AnswerPanel(props), (node) => node.type?.name === "SynthesisAnswer");
    const tree = synthesis.type(synthesis.props);
    find(tree, (node) => node.props?.className === "citation-document-link").props.onClick();
    const markdown = find(tree, (node) => node.type === MarkdownContent);
    const rendered = MarkdownContent(markdown.props);
    const inline = rendered.props.children.props.components.a({ href: "#citation-1", children: "[1]" });
    inline.props.onClick();
    assert.deepEqual(plain(opened), [
        { id: 21, title: "Budget policy", text: "" },
        { id: 21, title: "Budget policy", text: "" },
    ]);
    const missing = renderToStaticMarkup(createElement(AnswerPanel, { ...props, result: { mode: "Synthesis", response: { ...response, citations: [{ ...citation, itemKey: "missing" }] } } }));
    assert.doesNotMatch(missing, /class="inline-citation"|class="citation-document-link"/);
    assert.match(missing, /Document preview unavailable/);
});

const entities = [
    { id: 2, name: "Zoe", type: "PERSON", summary: "Finance lead", verified: false, documentCount: 2 },
    { id: 3, name: "Acme", type: "COMPANY", summary: "Company", verified: false, documentCount: 5 },
    { id: 1, name: "Alex", type: "PERSON", summary: "Project owner", verified: true, documentCount: 1 },
    { id: 4, name: "Cedar", type: "PROJECT", summary: "Delivery project", verified: false, documentCount: 3 },
    { id: 5, name: "Finance", type: "TEAM", summary: "Budget owners", verified: false, documentCount: 4 },
];

test("entities are organized by type, sorted by name and presented in a closed disclosure", () => {
    const groups = groupDocumentEntities(entities);
    assert.deepEqual(
        Array.from(groups, (group) => group.label),
        ["People", "Companies", "Projects", "Teams"],
    );
    assert.deepEqual(
        Array.from(groups[0].entities, (entity) => entity.name),
        ["Alex", "Zoe"],
    );
    assert.equal(entities[0].name, "Zoe", "grouping must preserve the API result order");
    const html = renderToStaticMarkup(
        createElement(DocumentEntitiesPanel, {
            entities: { status: "COMPLETED", truncated: false, errorCode: null, entities },
            loading: false,
            error: null,
            busy: false,
            hasDocument: true,
            canExtract: false,
            onReload() {},
            onExtract() {},
            onOpenEntity() {},
        }),
    );
    assert.match(html, /<details/);
    assert.doesNotMatch(html, /<details[^>]*\sopen/);
    assert.match(html, /People/);
    assert.match(html, /Project owner/);
    assert.match(html, /Verified/);
    assert.match(html, /Unverified/);
    assert.doesNotMatch(html, /Retry entity extraction/);
});

function dialogHarness({
    id = 7,
    content = async () => ({ id: 7, title: "Current title", type: "Policy", text: "---\nowner: Finance\n---\n# Readable document" }),
    detected = async () => {
        throw new Error("Entity service unavailable");
    },
} = {}) {
    const state = [];
    const effects = [];
    let cursor = 0;
    let mounted = false;
    let closed = 0;
    let modalClosed = 0;
    let opened = 0;
    const navigated = [];
    function MarkdownContent() {}
    function DocumentEntitiesPanel() {}
    const component = load("DetailDialog.tsx", {
        react: {
            useState: (initial) => {
                const index = cursor++;
                if (!(index in state)) state[index] = initial;
                return [
                    state[index],
                    (value) => {
                        state[index] = value;
                    },
                ];
            },
            useRef: () => ({ current: { showModal: () => opened++, close: () => modalClosed++ } }),
            useMemo: (factory) => factory(),
            useEffect: (effect) => {
                if (!mounted) effects.push(effect);
            },
        },
        "react-router-dom": { useNavigate: () => (path) => navigated.push(path) },
        "../../features/auth/useAuth": { useAuth: () => ({ user: { role: "ADMIN" } }) },
        "../../features/entities/entitiesApi": { getDocumentContent: content, getDocumentEntities: detected },
        "./MarkdownContent": { MarkdownContent },
        "./DocumentEntitiesPanel": { DocumentEntitiesPanel },
    }).DetailDialog;
    const render = () => {
        cursor = 0;
        return component({ detail: { id, title: "Old title", text: "Preview" }, onClose: () => closed++ });
    };
    function find(node, predicate) {
        if (!node || typeof node !== "object") return null;
        if (Array.isArray(node)) {
            for (const child of node) {
                const result = find(child, predicate);
                if (result) return result;
            }
            return null;
        }
        if (predicate(node)) return node;
        return find(node.props?.children, predicate);
    }
    render();
    mounted = true;
    const cleanup = effects.map((effect) => effect());
    return { render, find, navigated, cleanup: () => cleanup.forEach((cancel) => cancel?.()), counts: () => ({ closed, modalClosed, opened }) };
}

test("entity lookup errors do not hide document content, metadata starts closed, and the X closes the native dialog", async () => {
    const preview = dialogHarness();
    await new Promise((resolve) => setImmediate(resolve));
    const tree = preview.render();
    assert.match(tree.props.className, /document-dialog/);
    assert.equal(preview.find(tree, (node) => node.type === "h2").props.children, "Current title");
    assert.equal(preview.find(tree, (node) => node.type?.name === "MarkdownContent").props.text, "# Readable document");
    assert.equal(preview.find(tree, (node) => node.type === "details").props.open, undefined);
    assert.equal(preview.find(tree, (node) => node.type?.name === "DocumentEntitiesPanel").props.error, "Entity service unavailable");
    preview.find(tree, (node) => node.props?.["aria-label"] === "Close document").props.onClick();
    assert.equal(preview.counts().modalClosed, 1);
    assert.equal(preview.counts().opened, 1);
    tree.props.onClose();
    assert.equal(preview.counts().closed, 1);
    preview.cleanup();
});

test("document errors remain explicit, entity links navigate, and late responses cannot update a closed preview", async () => {
    const failed = dialogHarness({
        content: async () => {
            throw new Error("Document unavailable");
        },
        detected: async () => ({ status: "COMPLETED", entities }),
    });
    await new Promise((resolve) => setImmediate(resolve));
    let tree = failed.render();
    assert.equal(failed.find(tree, (node) => node.props?.role === "alert").props.children, "Document unavailable");
    assert.equal(
        failed.find(tree, (node) => node.type?.name === "MarkdownContent"),
        null,
    );
    failed.find(tree, (node) => node.type?.name === "DocumentEntitiesPanel").props.onOpenEntity(3);
    assert.deepEqual(failed.navigated, ["/entities?entity=3"]);
    failed.cleanup();
    let finish;
    const late = dialogHarness({
        content: () =>
            new Promise((resolve) => {
                finish = resolve;
            }),
        detected: async () => ({ status: "COMPLETED", entities: [] }),
    });
    late.cleanup();
    finish({ title: "Late title", text: "Late body", type: "Note" });
    await new Promise((resolve) => setImmediate(resolve));
    tree = late.render();
    assert.equal(late.find(tree, (node) => node.type === "h2").props.children, "Old title");
});

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
const { MarkdownDocument } = load("MarkdownDocument.tsx");
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
        createElement(MarkdownDocument, {
            text: "# Budget review\n\nA **clear** and *readable* plan.\n\n| Team | Budget |\n| --- | --- |\n| Finance | $5,000 |\n\n- [x] Approved\n- [ ] Pending\n\n> Keep the context.\n\n```js\nconst approved = true;\n```\n\n[Policy](https://example.test/policy)",
        }),
    );
    for (const tag of ["<h1>", "<strong>", "<em>", "<table>", "<blockquote>", "<pre>", "<code"]) assert.ok(html.includes(tag), tag);
    assert.match(html, /type="checkbox"/);
    assert.match(html, /checked=""/);
    assert.match(html, /target="_blank" rel="noopener noreferrer"/);
});

test("unsafe HTML and links are blocked, and remote images are represented as links", () => {
    const html = renderToStaticMarkup(createElement(MarkdownDocument, { text: '<script>alert("private")</script>\n\n[Unsafe](javascript:alert%281%29)\n\n![Chart](https://example.test/tracker.png)\n\n<img src=x onerror=alert(1)>' }));
    assert.doesNotMatch(html, /<script|javascript:|<img|onerror=/);
    assert.match(html, /Image: Chart/);
    assert.match(html, /href="https:\/\/example.test\/tracker.png"/);
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
    function MarkdownDocument() {}
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
        "./MarkdownDocument": { MarkdownDocument },
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
    assert.equal(preview.find(tree, (node) => node.type?.name === "MarkdownDocument").props.text, "# Readable document");
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
        failed.find(tree, (node) => node.type?.name === "MarkdownDocument"),
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

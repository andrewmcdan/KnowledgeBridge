import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import { runInNewContext } from "node:vm";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";

const packageRequire = createRequire(import.meta.url);
const contract = JSON.parse(readFileSync(new URL("../../Docs/openapi.json", import.meta.url), "utf8"));
const item = { ...contract.components.schemas.KnowledgeItem.example, id: 41, title: "Access policy" };
function load(name, overrides = {}, globals = {}) {
    function read(url) {
        const module = { exports: {} };
        runInNewContext(ts.transpileModule(readFileSync(url, "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText, {
            module,
            exports: module.exports,
            Error,
            Event,
            CustomEvent,
            URLSearchParams,
            ...globals,
            require: (specifier) => {
                if (specifier in overrides) return overrides[specifier];
                if (!specifier.startsWith(".")) return packageRequire(specifier);
                const path = new URL(specifier + ".ts", url);
                return read(existsSync(path) ? path : new URL(specifier + ".tsx", url));
            },
        });
        return module.exports;
    }
    return read(new URL(`../src/features/knowledge/${name}`, import.meta.url));
}
const storage = { sessionStorage: { getItem: () => "current-token" }, localStorage: { getItem: () => null }, window: new EventTarget() };
const flush = async () => {
    for (let i = 0; i < 8; i++) await Promise.resolve();
};
const nodes = (node) => (!node || typeof node !== "object" ? [] : [node, ...[node.props?.children].flat(Infinity).flatMap(nodes)]);

test("lifecycle API uses authenticated admin routes, validates inputs, and accepts 204", async () => {
    const calls = [];
    const { changeKnowledgeLifecycle } = load(
        "knowledgeApi.ts",
        {},
        {
            ...storage,
            fetch: async (url, options) => {
                calls.push({ url, options });
                return new Response(null, { status: 204 });
            },
        },
    );
    for (const [action, method, suffix] of [
        ["deactivate", "POST", "/deactivate"],
        ["activate", "POST", "/activate"],
        ["trash", "DELETE", ""],
        ["restore", "POST", "/restore"],
        ["permanent", "DELETE", "/permanent"],
    ]) {
        await changeKnowledgeLifecycle(41, action);
        const call = calls.at(-1);
        assert.equal(call.url, `/api/knowledge-items/41${suffix}`);
        assert.equal(call.options.method, method);
        assert.equal(call.options.headers.Authorization, "Bearer current-token");
        assert.equal(call.options.body, undefined);
    }
    for (const id of [0, -1, 1.5, NaN, Number.MAX_SAFE_INTEGER + 1]) await assert.rejects(changeKnowledgeLifecycle(id, "trash"), /identity/);
    await assert.rejects(changeKnowledgeLifecycle(41, "__proto__"), /action/);
    assert.equal(calls.length, 5);
});

test("lifecycle errors preserve server failures and malformed lifecycle data is rejected", async () => {
    const api = load("knowledgeApi.ts", {}, { ...storage, fetch: async () => Response.json({ code: "FORBIDDEN", message: "Administrators only." }, { status: 403 }) });
    await assert.rejects(api.changeKnowledgeLifecycle(41, "permanent"), /Administrators only/);
    for (const invalid of [
        { ...item, lifecycleState: "UNKNOWN" },
        { ...item, lifecycleState: "TRASH" },
        { ...item, trashedFrom: "INACTIVE" },
    ]) {
        const client = load("knowledgeApi.ts", {}, { ...storage, fetch: async () => Response.json([invalid]) });
        await assert.rejects(client.getKnowledgeItems(), /invalid knowledge list/);
    }
});

function pageHarness(role = "ADMIN", initial = [item], view = "ACTIVE") {
    const states = [],
        refs = [],
        requests = [];
    let cursor = 0,
        refCursor = 0,
        cleanup,
        effectStarted = false,
        unmounted = false,
        lateWrites = 0,
        snapshot = initial;
    const Dialog = () => null;
    const labels = { deactivate: "Deactivate", activate: "Activate", trash: "Move to Trash", restore: "Restore", permanent: "Permanently delete" };
    const { KnowledgeBasePage } = load("KnowledgeBasePage.tsx", {
        react: {
            useState: (initialValue) => {
                const index = cursor++;
                if (!(index in states)) states[index] = index === 3 ? view : initialValue;
                return [
                    states[index],
                    (value) => {
                        if (unmounted) lateWrites++;
                        states[index] = typeof value === "function" ? value(states[index]) : value;
                    },
                ];
            },
            useRef: (value) => refs[refCursor++] ?? (refs[refCursor - 1] = { current: value }),
            useEffect: (effect) => {
                if (!effectStarted) {
                    effectStarted = true;
                    cleanup = effect();
                }
            },
        },
        "../auth/useAuth": { useAuth: () => ({ user: { role } }) },
        "./KnowledgeActionDialog": { KnowledgeActionDialog: Dialog },
        "./knowledgeApi": { knowledgeActionLabels: labels, getKnowledgeItems: async () => snapshot, changeKnowledgeLifecycle: (id, action) => new Promise((resolve, reject) => requests.push({ id, action, resolve, reject })) },
        "../../components/ui/DocumentRow": { DocumentRow: ({ document }) => createElement("p", null, document.title) },
    });
    const render = () => {
        cursor = refCursor = 0;
        return KnowledgeBasePage({ search: "", onOpenDetail() {} });
    };
    render();
    return {
        render,
        requests,
        dialog: () => nodes(render()).find((node) => node.type === Dialog)?.props,
        act: (action, title = item.title) =>
            nodes(render())
                .find((node) => node.props?.["aria-label"] === `${labels[action]} ${title}`)
                .props.onClick(),
        snapshot: (value) => {
            snapshot = value;
        },
        unmount: () => {
            unmounted = true;
            cleanup();
        },
        lateWrites: () => lateWrites,
    };
}

test("only admins see management actions, with state-specific controls and busy-ingestion guards", async () => {
    const inactive = { ...item, id: 42, title: "Inactive policy", lifecycleState: "INACTIVE" };
    const trash = { ...item, id: 43, title: "Trashed policy", lifecycleState: "TRASH", trashedFrom: "ACTIVE", softDeletedAt: "2026-11-02T00:00:00Z" };
    const user = pageHarness("USER", [item, inactive, trash]);
    await flush();
    const html = renderToStaticMarkup(user.render());
    assert.match(html, /Access policy/);
    assert.doesNotMatch(html, /Deactivate|Move to Trash|Permanently delete|Inactive policy|Trashed policy|Document state/);
    const admin = pageHarness("ADMIN", [item, inactive, trash], "ALL");
    await flush();
    const controls = nodes(admin.render())
        .filter((node) => node.type === "button")
        .map((node) => node.props["aria-label"]);
    for (const label of ["Deactivate Access policy", "Activate Inactive policy", "Restore Trashed policy", "Permanently delete Trashed policy"]) assert.ok(controls.includes(label));
    const busy = pageHarness("ADMIN", [{ ...item, status: "PROCESSING" }]);
    await flush();
    assert.ok(
        nodes(busy.render())
            .filter((node) => node.type === "button" && node.props?.["aria-label"]?.includes(item.title))
            .every((node) => node.props.disabled),
    );
});

test("confirmation serializes mutations, refreshes server state, and keeps failed actions retryable", async () => {
    const page = pageHarness();
    await flush();
    page.act("deactivate");
    const dialog = page.dialog();
    dialog.onConfirm();
    dialog.onConfirm();
    dialog.onCancel();
    assert.equal(page.requests.length, 1);
    assert.equal(page.dialog().busy, true);
    page.requests[0].reject(new Error("Engine unavailable"));
    await flush();
    assert.equal(page.dialog().busy, false);
    assert.match(page.dialog().error, /Engine unavailable/);
    page.dialog().onConfirm();
    page.snapshot([{ ...item, lifecycleState: "INACTIVE" }]);
    page.requests[1].resolve();
    await flush();
    assert.equal(page.dialog(), undefined);
    assert.doesNotMatch(renderToStaticMarkup(page.render()), /aria-label="Access policy"/);
    assert.match(renderToStaticMarkup(page.render()), /Deactivate completed/);
});

test("late mutation completion does not write state after leaving the page", async () => {
    const page = pageHarness();
    await flush();
    page.act("trash");
    page.dialog().onConfirm();
    page.unmount();
    page.requests[0].resolve();
    await flush();
    assert.equal(page.lateWrites(), 0);
});

test("permanent deletion requires typed confirmation and shows retention semantics", () => {
    let confirmation = "",
        confirmed = 0,
        cancelled = 0;
    const { KnowledgeActionDialog } = load("KnowledgeActionDialog.tsx", {
        react: {
            useRef: () => ({ current: null }),
            useEffect() {},
            useState: () => [
                confirmation,
                (value) => {
                    confirmation = value;
                },
            ],
        },
    });
    const render = (busy = false) => KnowledgeActionDialog({ item, action: "permanent", busy, error: null, onConfirm: () => confirmed++, onCancel: () => cancelled++ });
    const event = { preventDefault() {} };
    nodes(render())
        .find((node) => node.type === "form")
        .props.onSubmit(event);
    assert.equal(confirmed, 0);
    assert.match(renderToStaticMarkup(render()), /physical engine cleanup follows its retention process/);
    nodes(render())
        .find((node) => node.type === "input")
        .props.onChange({ target: { value: "DELETE" } });
    nodes(render())
        .find((node) => node.type === "form")
        .props.onSubmit(event);
    assert.equal(confirmed, 1);
    nodes(render(true))
        .find((node) => node.type === "form")
        .props.onSubmit(event);
    render(true).props.onCancel(event);
    assert.equal(confirmed, 1);
    assert.equal(cancelled, 0);
});

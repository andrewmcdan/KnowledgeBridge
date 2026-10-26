import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { runInNewContext } from "node:vm";
import ts from "typescript";

function loadApi(path, fetch) {
    const window = new EventTarget();
    function load(url) {
        const module = { exports: {} };
        const source = readFileSync(url, "utf8");
        runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, {
            module,
            exports: module.exports,
            fetch,
            window,
            Error,
            CustomEvent,
            TextEncoder,
            sessionStorage: { getItem: () => "current-token" },
            localStorage: { getItem: () => null },
            require: (specifier) => load(new URL(`${specifier}.ts`, url)),
        });
        return module.exports;
    }
    return { api: load(new URL(`../src/features/${path}.ts`, import.meta.url)), window };
}

const note = { id: 12, title: "Original", type: "Note", text: "Content", revision: 3 };
const contract = JSON.parse(readFileSync(new URL("../../Docs/openapi.json", import.meta.url), "utf8"));
const item = contract.components.schemas.KnowledgeItem.example;
const totals = { scope: "personal", queryCount: 2, inputTokens: 10, outputTokens: 5, totalTokens: 15, cacheHits: 1, estimatedQueryCount: 1 };

test("editing sends the captured revision, preserves text, and accepts a saved note whose reindex failed", async () => {
    const requests = [];
    const { api } = loadApi("knowledge/knowledgeApi", async (path, options) => {
        requests.push({ path, options });
        return Response.json(options.method === "PUT" ? { ...item, status: "FAILED", lastErrorCode: "TIMEOUT", lastErrorMessage: "Engine unavailable" } : note);
    });
    const loaded = await api.getManualNote(12);
    const saved = await api.updateManualNote({ ...loaded, title: " Updated ", type: " Policy ", text: "New text\n" });
    assert.equal(saved.status, "FAILED");
    assert.equal(requests[0].path, "/api/knowledge-items/12/manual");
    assert.equal(requests[1].options.method, "PUT");
    assert.equal(requests[1].options.headers.Authorization, "Bearer current-token");
    assert.deepEqual(JSON.parse(requests[1].options.body), { title: "Updated", type: "Policy", text: "New text\n", revision: 3 });
});

test("stale edits surface the server conflict and rejected edits expire only the captured session", async () => {
    const conflict = loadApi("knowledge/knowledgeApi", async () => Response.json({ code: "NOTE_CONFLICT", message: "Reload the note before saving." }, { status: 409 }));
    await assert.rejects(conflict.api.updateManualNote(note), { message: "Reload the note before saving." });
    const { api, window } = loadApi("knowledge/knowledgeApi", async () => Response.json({ code: "UNAUTHENTICATED", message: "Expired" }, { status: 401 }));
    const expired = [];
    window.addEventListener("knowledgebridge:auth-expired", (event) => expired.push(event.detail));
    await assert.rejects(api.updateManualNote(note), { message: "Expired" });
    assert.deepEqual(expired, ["Bearer current-token"]);
});

test("note payloads require an editable revision and validation enforces the UTF-8 body limit", async () => {
    for (const invalid of [null, {}, { ...note, revision: 0 }, { ...note, revision: 1.5 }, { ...note, text: null }]) {
        await assert.rejects(loadApi("knowledge/knowledgeApi", async () => Response.json(invalid)).api.getManualNote(12), { message: "The server returned an invalid note." });
    }
    const { api } = loadApi("knowledge/manualNotes", async () => {});
    assert.match(api.validateManualNote(" ", "Note", "Text"), /title/);
    assert.match(api.validateManualNote("Title", " ", "Text"), /type/);
    assert.match(api.validateManualNote("Title", "Note", " "), /content/);
    assert.equal(api.validateManualNote("Title", "Note", "x".repeat(1024 * 1024 - 1)), null);
    assert.match(api.validateManualNote("Title", "Note", "é".repeat(1024 * 512)), /1 MiB/);
});

test("usage totals use the authenticated endpoint and reject invalid scopes or counters", async () => {
    const { api } = loadApi("usage/usageApi", async (path, options) => {
        assert.equal(path, "/api/usage");
        assert.equal(options.headers.Authorization, "Bearer current-token");
        return Response.json(totals);
    });
    assert.equal((await api.getUsageTotals()).totalTokens, 15);
    for (const invalid of [null, {}, { ...totals, scope: "other" }, { ...totals, totalTokens: -1 }, { ...totals, queryCount: "2" }]) {
        await assert.rejects(loadApi("usage/usageApi", async () => Response.json(invalid)).api.getUsageTotals(), { message: "The server returned invalid usage totals." });
    }
    await assert.rejects(loadApi("usage/usageApi", async () => new Response("<html>offline</html>", { status: 502 })).api.getUsageTotals(), { message: "We could not load usage totals." });
});

test("synthesis responses preserve cache and estimated usage markers and reject invalid counts", async () => {
    const response = {
        answer: "Answer",
        aiGenerated: true,
        extractiveFallback: false,
        citations: [],
        gaps: [],
        warnings: [],
        cacheHit: false,
        usage: { model: "unknown", inputTokens: 3, outputTokens: 2, estimatedCostUsd: null, estimated: true },
    };
    const { api } = loadApi("ask-ai/askAiApi", async () => Response.json(response));
    assert.equal((await api.submitQuestion("Synthesis", "Question")).response.usage.estimated, true);
    for (const invalid of [
        { ...response, cacheHit: "yes" },
        { ...response, usage: { ...response.usage, inputTokens: -1 } },
        { ...response, usage: { ...response.usage, estimated: undefined } },
    ]) {
        await assert.rejects(loadApi("ask-ai/askAiApi", async () => Response.json(invalid)).api.submitQuestion("Synthesis", "Question"), { message: "The server returned an invalid query response." });
    }
});

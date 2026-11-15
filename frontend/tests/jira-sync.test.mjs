import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import { runInNewContext } from "node:vm";
import ts from "typescript";

const packageRequire = createRequire(import.meta.url);
const storage = { sessionStorage: { getItem: () => "current-token" }, localStorage: { getItem: () => null }, window: new EventTarget() };
const run = {
    id: 3,
    projectKey: "ACME",
    status: "COMPLETED",
    startedAt: "2026-11-09T14:00:00Z",
    finishedAt: "2026-11-09T14:01:00Z",
    createdCount: 22,
    updatedCount: 1,
    unchangedCount: 2,
    failedCount: 0,
    errorCode: null,
    errorMessage: null,
};

function load(fetch) {
    function read(url) {
        const module = { exports: {} };
        runInNewContext(ts.transpileModule(readFileSync(url, "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, {
            module,
            exports: module.exports,
            Error,
            Event,
            CustomEvent,
            URLSearchParams,
            encodeURIComponent,
            Date,
            fetch,
            ...storage,
            require: (specifier) => (specifier.startsWith(".") ? read(new URL(`${specifier}.ts`, url)) : packageRequire(specifier)),
        });
        return module.exports;
    }
    return read(new URL("../src/features/knowledge/jiraSyncApi.ts", import.meta.url));
}

const respond =
    (body, status = 200) =>
    async () =>
        new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

test("starting a sync posts to the project with the signed-in token", async () => {
    const calls = [];
    const api = load(async (url, init) => {
        calls.push([url, init]);
        return new Response(JSON.stringify({ ...run, status: "RUNNING", finishedAt: null }), { status: 202 });
    });

    const started = await api.startJiraSync("ACME");

    assert.equal(started.status, "RUNNING");
    assert.equal(calls[0][0], "/api/integrations/jira/projects/ACME/sync");
    assert.equal(calls[0][1].method, "POST");
    assert.equal(calls[0][1].headers.Authorization, "Bearer current-token");
});

test("lists are validated before the panel uses them", async () => {
    assert.equal((await load(respond([run])).getJiraSyncRuns()).length, 1);
    assert.equal((await load(respond([{ key: "ACME", name: "Acme" }])).getJiraProjects())[0].key, "ACME");
    await assert.rejects(load(respond([{ ...run, status: "PAUSED" }])).getJiraSyncRuns(), /invalid Jira response/);
    await assert.rejects(load(respond({ key: "ACME" })).getJiraProjects(), /invalid Jira response/);
    await assert.rejects(load(respond({ configured: "yes", reachable: true })).getJiraStatus(), /invalid Jira response/);
});

test("a sync that is already running surfaces the server's error", async () => {
    await assert.rejects(load(respond({ code: "CONFLICT", message: "The resource is not in a state that allows this operation." }, 409)).startJiraSync("ACME"), /not in a state/);
});

test("sync status reads naturally in each state", () => {
    const { describeJiraSyncRun } = load(async () => new Response());
    assert.match(describeJiraSyncRun(run), /^Last sync of ACME finished .*: 22 added, 1 updated, 2 unchanged, 0 failed\.$/);
    assert.equal(describeJiraSyncRun({ ...run, status: "RUNNING", finishedAt: null }), "Syncing ACME: 22 added, 1 updated, 2 unchanged, 0 failed so far…");
    assert.equal(describeJiraSyncRun({ ...run, status: "FAILED", errorMessage: "Jira is down" }), "Sync of ACME stopped: Jira is down (22 added, 1 updated, 2 unchanged, 0 failed).");
    assert.match(describeJiraSyncRun({ ...run, status: "FAILED", errorMessage: null }), /unknown error/);
});

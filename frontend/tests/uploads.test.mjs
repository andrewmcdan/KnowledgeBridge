import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { runInNewContext } from "node:vm";
import ts from "typescript";

function loadModule(name, globals = {}) {
    function load(url) {
        const source = readFileSync(url, "utf8");
        const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } });
        const module = { exports: {} };
        runInNewContext(outputText, { module, exports: module.exports, Error, ...globals, require: (specifier) => load(new URL(`${specifier}.ts`, url)) });
        return module.exports;
    }
    return load(new URL(`../src/features/knowledge/${name}.ts`, import.meta.url));
}
const files = loadModule("uploadFiles");
const file = (name, path = name, size = 12) => ({ name, webkitRelativePath: path, size });

test("recognizes supported extensions without treating archives or suffixes as eligible", () => {
    for (const name of ["a.md", "b.DOCX", "c.MP4", "d.mov", "e.webm"]) assert.ok(files.uploadKind(name));
    for (const name of ["a.pdf", "b.zip", "c.mp4.exe", "d.doc", "e.markdown"]) assert.equal(files.uploadKind(name), null);
    assert.equal(files.uploadTitle("Kickoff.MOV"), "Kickoff");
    assert.equal(files.uploadTitle("a".repeat(300) + ".md").length, 255);
});

test("preserves nested paths and same-named files in different subfolders", () => {
    const result = files.folderEntries([file("readme.md", "root/a/readme.md"), file("readme.md", "root/b/readme.md"), file("talk.mp4", "root/b/deep/talk.mp4"), file("skip.pdf", "root/skip.pdf")]);
    assert.equal(result.skipped, 1);
    assert.deepEqual(
        Array.from(result.entries, (entry) => entry.path),
        ["root/a/readme.md", "root/b/deep/talk.mp4", "root/b/readme.md"],
    );
    assert.ok(result.entries.every((entry) => entry.status === "ready"));
    assert.equal(files.folderEntries([]).entries.length, 0);
});

test("validates file limits but leaves configurable video limits to the server", () => {
    assert.equal(files.validateUpload(file("ok.md", "", 1024 * 1024)), null);
    assert.match(files.validateUpload(file("big.md", "", 1024 * 1024 + 1)), /1 MiB/);
    assert.match(files.validateUpload(file("big.docx", "", 10 * 1024 * 1024 + 1)), /10 MiB/);
    assert.match(files.validateUpload(file("empty.mov", "", 0)), /content/);
    assert.equal(files.validateUpload(file("large.mp4", "", 600 * 1024 * 1024)), null);
    assert.match(files.validateUpload(file("a".repeat(256) + ".webm")), /255/);
});

test("uploads sequentially, continues on failures, and retries only unqueued entries", async () => {
    const entries = files.folderEntries([file("a.md"), file("b.docx"), file("c.mov"), file("empty.md", "", 0)]).entries;
    const attempted = [];
    let active = 0;
    let maxActive = 0;
    const update = (path, status, error) =>
        Object.assign(
            entries.find((entry) => entry.path === path),
            { status, error },
        );
    const result = await files.processFolderEntries(
        entries,
        async (entry) => {
            attempted.push(entry.path);
            maxActive = Math.max(maxActive, ++active);
            await Promise.resolve();
            active--;
            if (entry.file.name === "b.docx") throw new Error("Unreadable Word file");
        },
        update,
        () => false,
    );
    assert.equal(result.queued, 2);
    assert.equal(result.failed, 2);
    assert.equal(maxActive, 1);
    assert.deepEqual(attempted, ["a.md", "b.docx", "c.mov"]);
    assert.equal(entries.find((entry) => entry.path === "b.docx").error, "Unreadable Word file");
    const retried = [];
    await files.processFolderEntries(
        entries,
        async (entry) => retried.push(entry.path),
        update,
        () => false,
    );
    assert.deepEqual(retried, ["b.docx"]);
});

test("stops between uploads without resending or abandoning the current file", async () => {
    const entries = files.folderEntries([file("a.md"), file("b.md")]).entries;
    let stop = false;
    const attempted = [];
    await files.processFolderEntries(
        entries,
        async (entry) => {
            attempted.push(entry.path);
            stop = true;
        },
        () => {},
        () => stop,
    );
    assert.deepEqual(attempted, ["a.md"]);
});

test("processes the full nested selection, not just the first hundred preview rows", async () => {
    const entries = files.folderEntries(Array.from({ length: 125 }, (_, index) => file(`note-${index}.md`, `root/nested/note-${index}.md`))).entries;
    let uploaded = 0;
    const result = await files.processFolderEntries(
        entries,
        async () => {
            uploaded++;
        },
        () => {},
        () => false,
    );
    assert.equal(uploaded, 125);
    assert.equal(result.queued, 125);
});

test("reuses the document route for Markdown/Word and the video route for all video formats", async () => {
    const requests = [];
    const api = loadModule("knowledgeApi", {
        sessionStorage: { getItem: () => "synthetic-token" },
        localStorage: { getItem: () => null },
        FormData,
        fetch: async (path, options) => {
            requests.push({ path, options });
            return {
                ok: true,
                status: 202,
                json: async () => ({
                    id: 1,
                    title: "Title",
                    type: "Document",
                    sourceKind: "MANUAL",
                    ownerId: 1,
                    status: "PENDING",
                    characterCount: 0,
                    externalEngineId: null,
                    lastErrorCode: null,
                    lastErrorMessage: null,
                    createdAt: "now",
                    updatedAt: "now",
                }),
            };
        },
    });
    for (const extension of ["md", "docx", "MP4", "mov", "webm"]) {
        await api.uploadKnowledgeFile(new File(["synthetic"], `sample.${extension}`), "Title", "Document");
    }
    assert.deepEqual(
        requests.map((request) => request.path),
        ["/api/knowledge-items/upload", "/api/knowledge-items/upload", "/api/knowledge-items/video", "/api/knowledge-items/video", "/api/knowledge-items/video"],
    );
    assert.ok(requests.every(({ options }) => options.method === "POST" && options.body.get("title") === "Title" && options.headers.Authorization === "Bearer synthetic-token"));
    await assert.rejects(api.uploadKnowledgeFile(new File(["x"], "bad.pdf"), "Title", "Document"), /not supported/);
    assert.equal(requests.length, 5);
});

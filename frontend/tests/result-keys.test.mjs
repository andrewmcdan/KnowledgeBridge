import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { runInNewContext } from "node:vm";
import ts from "typescript";

const module = { exports: {} };
const source = readFileSync(new URL("../src/features/ask-ai/resultKeys.ts", import.meta.url), "utf8");
runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, { module, exports: module.exports });
const { resultKeys } = module.exports;

test("repeated documents and snippets keep every row unique and preserve identities when reranked", () => {
    const rows = [
        { itemKey: "42", externalId: "doc/42", snippet: "same" },
        { itemKey: "42", externalId: "doc/42", snippet: "different" },
        { itemKey: "42", externalId: "doc/42", snippet: "same" },
        { itemKey: "43", externalId: "doc/42", snippet: "same" },
    ];
    for (const identity of [(row) => [row.itemKey, row.externalId, row.snippet], (row) => [row.itemKey, row.externalId]]) {
        const keys = Array.from(resultKeys(rows, identity));
        assert.equal(new Set(keys).size, rows.length);
        assert.deepEqual(Array.from(resultKeys(rows, identity)), keys);
        const reordered = [rows[3], ...rows.slice(0, 3)];
        assert.deepEqual(Array.from(resultKeys(reordered, identity)), [keys[3], ...keys.slice(0, 3)]);
        const updated = [...rows, { itemKey: "44", externalId: "doc/44", snippet: "new" }];
        assert.deepEqual(Array.from(resultKeys(updated, identity)).slice(0, 4), keys);
    }
});

test("structured identities cannot collide through separators embedded in engine values", () => {
    const keys = resultKeys(
        [
            ["a-b", "c"],
            ["a", "b-c"],
            ["a-b", "c"],
        ],
        (row) => row,
    );
    assert.equal(new Set(keys).size, 3);
});

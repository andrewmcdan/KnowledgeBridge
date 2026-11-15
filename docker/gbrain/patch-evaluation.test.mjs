import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { runInNewContext } from "node:vm";

// Execute the patch's actual prompt replacements with controlled prompt builders.
// This checks opt-in behavior offline; it does not run the upstream engine or a model.
const patch = readFileSync(new URL("./patch-evaluation.mjs", import.meta.url), "utf8");
const promptSource = `
  const systemPrompt = buildThinkSystemPrompt({});
  const userMessage = buildThinkUserMessage({});
  ({ systemPrompt, userMessage });
`;
let patchedPrompt = promptSource;
runInNewContext(patch.slice(patch.indexOf('edit("src/core/think/index.ts"')), {
    edit(file, replacements) {
        if (file !== "src/core/think/index.ts") return;
        for (const [before, after] of replacements) {
            patchedPrompt = patchedPrompt.replace(before, after);
        }
    },
});

function buildPrompt(evaluation) {
    return runInNewContext(patchedPrompt, {
        opts: { evaluation },
        buildThinkSystemPrompt: () => "Original system prompt",
        buildThinkUserMessage: () => "Original user message",
    });
}

test("ordinary synthesis preserves both original prompts without evaluation options", () => {
    for (const evaluation of [undefined, null, false]) {
        const result = buildPrompt(evaluation);
        assert.equal(result.systemPrompt, "Original system prompt");
        assert.equal(result.userMessage, "Original user message");
    }
});

test("explicit evaluation adds source discipline while preserving the user prompt", () => {
    const result = buildPrompt({ model: "diagnostic-model", allowed_slugs: [] });
    assert.ok(result.systemPrompt.startsWith("Original system prompt\nSource discipline:"));
    assert.ok(result.systemPrompt.includes("Cite the evidence supporting each material claim."));
    assert.equal(result.userMessage, "Original user message");
});

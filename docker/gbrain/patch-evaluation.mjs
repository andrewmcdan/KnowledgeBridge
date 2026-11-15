// Opt-in evaluation instrumentation for the pinned engine. Every anchor is checked:
// an upstream upgrade must be reviewed rather than silently dropping evidence tracing.
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const root = process.argv[2] ?? "/opt/gbrain";
function edit(file, replacements) {
    let source = readFileSync(join(root, file), "utf8");
    for (const [before, after] of replacements) {
        if (source.split(before).length !== 2) throw new Error(`Evaluation patch anchor mismatch: ${file}: ${before.slice(0, 70)}`);
        source = source.replace(before, after);
    }
    writeFileSync(join(root, file), source);
}

edit("src/core/think/index.ts", [
    ["export interface RunThinkOpts {", `export interface RunThinkOpts {
  evaluation?: { model: string; allowed_slugs: string[] };`],
    ["export interface ThinkResult {", `export interface ThinkResult {
  evaluation_trace?: Record<string, unknown>;`],
    ["const thinkDecide = await startThinkDecide", "const thinkDecide = opts.evaluation ? undefined : await startThinkDecide"],
    ["  // D6: per-stream gather failures", `  // KnowledgeBridge evaluation: retain pre-filter candidates, then scope this request only.
  // Takes and trajectory are disabled in diagnostic arms so all supplied evidence is inspectable.
  const evaluationCandidates = opts.evaluation ? gather.pages.map(p => ({
    slug: p.slug, source_id: p.source_id, chunk_id: p.chunk_id, score: p.score,
    title: p.title, chunk_text: p.chunk_text,
  })) : undefined;
  if (opts.evaluation) {
    const allowed = new Set(opts.evaluation.allowed_slugs);
    gather.pages = gather.pages.filter(p => /^knowledgebridge\\/[1-9][0-9]*$/.test(p.slug)
      && (allowed.size === 0 || allowed.has(p.slug)));
    gather.takes = [];
    gather.graphSlugs = [];
  }
  // D6: per-stream gather failures`],
    ["  const systemPrompt = buildThinkSystemPrompt({", "  let systemPrompt = buildThinkSystemPrompt({"],
    ["  const userMessage = buildThinkUserMessage({", `  if (opts.evaluation) {
    systemPrompt += '\\nSource discipline: distinguish questions, examples, proposals and quoted misconceptions from asserted facts. '
      + 'For historical questions use the policy effective for the requested period, not current thresholds. '
      + 'Prefer controlled source records over drafts and informal notes. Never invent names, numbers, approvals or dates. '
      + 'State what is missing when the supplied evidence cannot answer. Cite the evidence supporting each material claim.';
  }
  const userMessage = buildThinkUserMessage({`],
    ["  // #1698: true only when an actual synthesis", `  const evaluationTrace = opts.evaluation ? {
    version: 1, candidates: evaluationCandidates,
    selected_pages: gather.pages.map(p => ({ slug: p.slug, source_id: p.source_id,
      chunk_id: p.chunk_id, title: p.title, score: p.score, chunk_text: p.chunk_text })),
    system_prompt: systemPrompt, user_message: userMessage,
    context_sha256: new Bun.CryptoHasher('sha256').update(systemPrompt + '\\0' + userMessage).digest('hex'),
    scope: opts.evaluation.allowed_slugs.length ? 'corpus_only' : 'workspace',
    filtering_stage: 'after_gather_before_prompt',
    takes_and_trajectory: 'disabled_for_controlled_comparison',
  } : undefined;
  // #1698: true only when an actual synthesis`],
    ["    usage, ...(evidenceDelivery", "    ...(evaluationTrace ? { evaluation_trace: evaluationTrace } : {}),\n    usage, ...(evidenceDelivery"],
    ["        usage: null,         // [E2] no LLM ran", "        ...(evaluationTrace ? { evaluation_trace: evaluationTrace } : {}),\n        usage: null,         // [E2] no LLM ran"],
]);

edit("src/core/verbs.ts", [
    ["    question: { type: 'string', required: true, description: 'The question to answer.' },", `    question: { type: 'string', required: true, description: 'The question to answer.' },
    evaluation: { type: 'object', description: 'Opt-in KnowledgeBridge diagnostic options; disabled unless configured.' },`],
    ["    const { runThink } = await import('./think/index.ts');", `    let evaluation: { model: string; allowed_slugs: string[] } | undefined;
    if (p.evaluation !== undefined) {
      if (await ctx.engine.getConfig('evaluation.enabled') !== 'true')
        throw verbError('permission_denied', 'Evaluation tracing is disabled.', 'Enable it explicitly on the engine host.');
      const input = p.evaluation as Record<string, unknown>;
      const models = (await ctx.engine.getConfig('evaluation.models') ?? '').split(',').map(s => s.trim());
      if (!input || typeof input.model !== 'string' || !models.includes(input.model))
        throw verbError('invalid_params', 'Evaluation model is not allowed.', 'Use a configured evaluation model.');
      const slugs = input.allowed_slugs;
      if (!Array.isArray(slugs) || slugs.length > 100 || slugs.some(s => typeof s !== 'string' || !/^knowledgebridge\\/[1-9][0-9]*$/.test(s)))
        throw verbError('invalid_params', 'Invalid evaluation document scope.', 'Supply up to 100 application document slugs.');
      evaluation = { model: input.model, allowed_slugs: slugs };
    }
    const { runThink } = await import('./think/index.ts');`],
    ["      takesHoldersAllowList: ctx.takesHoldersAllowList,", `      ...(evaluation ? { evaluation, model: evaluation.model, modelExplicit: true, withTrajectory: false } : {}),
      takesHoldersAllowList: ctx.takesHoldersAllowList,`],
    ["          protocol_version: MEMORY_VERBS_VERSION,", "          ...(result.evaluation_trace ? { evaluation_trace: result.evaluation_trace } : {}),\n          protocol_version: MEMORY_VERBS_VERSION,"],
    ["      protocol_version: MEMORY_VERBS_VERSION,\n    };\n  },\n  cliHints: { name: 'synthesize'", "      ...(result.evaluation_trace ? { evaluation_trace: result.evaluation_trace } : {}),\n      protocol_version: MEMORY_VERBS_VERSION,\n    };\n  },\n  cliHints: { name: 'synthesize'"],
]);

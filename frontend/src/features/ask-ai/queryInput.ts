import type { AskAiMode } from "./askAiApi";

export const DEFAULT_SEARCH_LIMIT = 10;

/** Match Java String.strip / Character.isWhitespace, including its nonbreaking-space exclusions. */
export function normalizeQuestion(question: string): string {
    // These control characters are deliberately part of Java's whitespace contract.
    // oxlint-disable-next-line no-control-regex
    return question.replace(/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu, "");
}

export function questionError(mode: AskAiMode, question: string): string | null {
    const normalized = normalizeQuestion(question);
    if (!normalized) return "Question is required.";
    if (normalized.includes("\u0000")) return "Question must not contain NUL characters.";
    const max = mode === "Search" ? 1000 : 2000;
    return normalized.length > max ? `${mode} questions must be at most ${max} characters.` : null;
}

export function validSearchLimit(limit: number): boolean {
    return Number.isInteger(limit) && limit >= 1 && limit <= 50;
}

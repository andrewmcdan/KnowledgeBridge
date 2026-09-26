# KnowledgeBridge Agent Guide

## Scope

These instructions apply to the entire repository. Preserve existing architecture and keep changes focused on the requested task. Do not mix unrelated cleanup or generated output into a change.

## Repository boundaries

- Keep the React frontend and Spring Boot backend as separate applications.
- The frontend calls only the Spring `/api` boundary; it must not call gbrain or external AI providers directly.
- Keep all gbrain communication behind the backend `gbrain` adapter package.
- Do not commit secrets, root `.env`, build output, dependency directories, logs, or local database data.

## General behavior

- Read the existing implementation before making changes.
- Do not modify unrelated files.
- Prefer small, focused changes over broad refactors.
- Explain important architectural decisions.
- Do not install new dependencies without explaining why they are necessary.
- When diagnosing issues, check .env and/or .env.local for missing or incorrect environment variables.
- If .env/.env.example is missing environment variables, add them to both files comments explaining their purpose and usage.
- Reuse existing code where possible. If a small refactor to existing code results in a smaller and more maintainable codebase, prefer that approach.

## Project stack

- This is a Next.js application using TypeScript.
- Use React components and server-side rendering where appropriate.
- Follow the existing CSS architecture and naming conventions.

## TypeScript

- Use strict TypeScript.
- Do not use `any` unless there is no reasonable alternative.
- Prefer explicit types at module boundaries.
- Validate external and user-provided data.
- Handle errors explicitly.

## Testing and verification

- Run the relevant tests after making changes.
- Run the TypeScript compiler or project type-check command.
- Run the configured linter.
- Run the configured code duplication check.
- Report any verification steps that could not be completed.

## Response format

At the end of each task, summarize:

1. What changed
2. Which files changed
3. What tests or checks were run
4. Any remaining concerns

## File formatting

Match the conventions established by the most recent formatting commit and by the file being edited.

- Save text files as UTF-8 with LF line endings and a final newline.
- Never reformat unrelated files or untouched sections merely to normalize style.
- TypeScript, TSX, CSS, and JSON use four spaces for indentation; do not use tabs.
- TypeScript and TSX use double quotes, semicolons, and trailing commas in multiline arrays and objects.
- Keep imports at the top, group `import type` with the other imports, and separate imports from declarations with one blank line.
- Expand nontrivial JSX across lines with nested elements indented one level. Keep very small, readable expressions inline when that matches nearby code.
- Put opening braces on the same line as TypeScript declarations and control statements.
- CSS uses one selector block per section, one declaration per line, four-space indentation, a space after colons, and spaces after commas.
- JSON uses four-space indentation and no trailing commas.
- Java follows the existing backend style: tabs for indentation, braces on the same line, and imports grouped as Java, third-party/Spring, project, then Jakarta where applicable.
- Markdown should use concise headings, blank lines around lists and code fences, and LF line endings.

The repository provides formatter commands that enforce these conventions. After frontend changes, run:

```powershell
cd frontend
npm run format:check
npm run lint
npm run build
```

To format frontend files, run `npm run format`. To format Java files, run `./gradlew.bat spotlessApply` from `backend`. After backend changes, run the formatting and coverage-gated check with Docker available:

```powershell
cd backend
.\gradlew.bat check
```

## Verification

- Verify behavior in proportion to the change; do not report mocks, fixtures, or process startup as proof of a live integration.
- For frontend behavior changes, verify both compilation and the affected route or interaction in a browser.
- Keep the working tree free of generated artifacts before committing.

# KnowledgeBridge Agent Guide

## Scope

These instructions apply to the entire repository. Preserve existing architecture and keep changes focused on the requested task. Do not mix unrelated cleanup or generated output into a change.

## Repository boundaries

- Keep the React frontend and Spring Boot backend as separate applications.
- The frontend calls only the Spring `/api` boundary; it must not call gbrain or external AI providers directly.
- Keep all gbrain communication behind the backend `gbrain` adapter package.
- Do not commit secrets, root `.env`, build output, dependency directories, logs, or local database data.

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

There is currently no repository-wide formatter command. Treat surrounding code and commit `3e07591` as the frontend formatting reference. After frontend changes, run:

```powershell
cd frontend
npm run lint
npm run build
```

After backend changes, run the coverage-gated check with Docker available:

```powershell
cd backend
.\gradlew.bat check
```

## Verification

- Verify behavior in proportion to the change; do not report mocks, fixtures, or process startup as proof of a live integration.
- For frontend behavior changes, verify both compilation and the affected route or interaction in a browser.
- Keep the working tree free of generated artifacts before committing.

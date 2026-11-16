# KnowledgeBridge user manual

The full manual is maintained as **17 separate Markdown chapters** in this directory. The PDF builder reads every `NN-*.md` chapter in numeric order and adds a professional cover, a generated clickable table of contents, PDF bookmarks, internal cross-references, and styled body pages. This README describes maintenance/building and is deliberately excluded from the reader-facing PDF.

## Read the sections

1. [About this manual](01-about-this-manual.md)
2. [Getting started](02-getting-started.md)
3. [Workspace navigation](03-workspace-navigation.md)
4. [Ask AI](04-ask-ai.md)
5. [Knowledge Base and document previews](05-knowledge-base.md)
6. [Entities](06-entities.md)
7. [Dashboard knowledge map](07-knowledge-map.md)
8. [Usage and costs](08-usage-and-costs.md)
9. [Adding documents and notes](09-adding-knowledge.md)
10. [Folder uploads](10-folder-uploads.md)
11. [Video ingestion](11-video-ingestion.md)
12. [Managing knowledge](12-managing-knowledge.md)
13. [Jira sync](13-jira-sync.md)
14. [User administration](14-user-administration.md)
15. [Administrator setup and operations](15-administrator-operations.md)
16. [Troubleshooting](16-troubleshooting.md)
17. [Quick reference and glossary](17-reference.md)

## Build the PDF

Use Python 3.10 or newer. The builder uses ReportLab, which the repository also uses for evaluation reports, and `markdown-it-py` for structured Markdown parsing. Neither a browser nor Word, LaTeX, or Pandoc is required. Fonts ship with ReportLab and are embedded in the PDF.

From the repository root:

```powershell
python -m pip install -r "Docs/User Manual/requirements.txt"
python -B "Docs/User Manual/build_manual.py" --check
python -B "Docs/User Manual/build_manual.py"
```

If the packages are already installed in your chosen Python environment, skip the install command. Use a virtual environment when appropriate for your machine. The build is entirely local and does not read `.env`, contact AI providers, start the application, or transmit manual content.

The default output is:

```text
Docs/User Manual/output/pdf/KnowledgeBridge-User-Manual.pdf
```

The default location is resolved relative to the script, so the command works from another working directory as well. For example, from inside this folder:

```powershell
python -B build_manual.py
python -B build_manual.py --output "output/pdf/KnowledgeBridge-User-Manual-review.pdf"
```

An explicit relative `--output` path is resolved from your current working directory. The output filename must end in `.pdf`. A successful build replaces an existing output only after the replacement PDF has finished building; a failed build preserves the previous output. The generated `output/` directory is ignored by Git so PDFs and render previews do not become source changes.

## Edit or extend the manual

- Keep each top-level section in its own `NN-lowercase-name.md` file. Numbers must be unique and consecutive, beginning at `01`.
- Begin each file with one H1 title, such as `# 4. Ask AI: search and cited answers`. Its number must match the filename. Use H2/H3 for subsections; H1/H2 enter the PDF contents and bookmarks.
- Write UTF-8 files with LF endings and a final newline. The builder checks the chapter files before generating output.
- Use paragraphs, emphasis, inline code, ordered/bulleted lists (including nested lists), blockquote notes, fenced code blocks, horizontal rules, and pipe tables. HTML is escaped; images and unsupported token types fail validation instead of being silently dropped.
- Link to another chapter using its Markdown filename, optionally with its Markdown heading fragment. The PDF converts these to internal destinations. All chapter link targets must exist.
- Relative links to other repository files are checked and become HTTPS links to the inspected product revision in `manual.json`. Normal HTTP/HTTPS and mail links remain external; no network access is needed to build them.
- Update this index when adding or renumbering chapters. The builder discovers the chapters; there is no separate hardcoded PDF section list to maintain.
- Update the title, subtitle, edition, publication date, product-source revision, and repository URL in `manual.json` when publishing a new edition. The source revision identifies the application behavior inspected, rather than guessing from the build machine's clock or branch.

The PDF uses US Letter pages, a navy/teal cover and headings, repeated table headers, wrapped code, and page numbers matching the physical PDF pages. Typography, colors, spacing, and cover drawing are defined in `build_manual.py`. Tables and paragraphs can continue across pages; each chapter starts on a new page. The contents are built in multiple passes so page numbers account for the contents' own length.

## Verify a change

Run the source checker, builder regression tests, and a fresh PDF build:

```powershell
python -B "Docs/User Manual/build_manual.py" --check
python -B -m unittest discover -s "Docs/User Manual" -p "test_build_manual.py" -v
python -B "Docs/User Manual/build_manual.py"
```

The optional PDF-structure assertions in the tests use `pypdf` when installed; the core build requires only the packages in `requirements.txt`. To enable those assertions in an environment without it, install `pypdf` separately. The tests report that optional check as skipped if it is unavailable.

Render the PDF after a meaningful content/style change and inspect every page, especially the cover, contents, bullet glyphs, tables crossing pages, code blocks, headers, footers, and short chapter endings. Poppler's `pdftoppm` can render all pages:

```powershell
pdftoppm -scale-to 1250 -png `
    "Docs/User Manual/output/pdf/KnowledgeBridge-User-Manual.pdf" `
    "Docs/User Manual/output/page"
```

Check that the table of contents and bookmarks include every section, displayed page numbers reach the intended pages, internal links work, and extracted text has no missing-glyph placeholders or clipped text. Source validation or a successfully created file alone is not proof of visual quality.

## Content verification boundaries

The chapters are grounded in the current UI components, backend contracts, validation/configuration, and maintained project documentation. Source references are listed in the final chapter. Planned features and placeholder controls are called out explicitly. The manual does not claim that a live provider, Jira site, or browser acceptance session was run while writing it. Recheck source behavior when adding screenshots or changing workflows.

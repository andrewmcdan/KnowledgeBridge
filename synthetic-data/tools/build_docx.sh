#!/usr/bin/env bash
# Rebuilds synthetic-data/docx/*.docx from tools/docx-sources/*.html with LibreOffice.
set -euo pipefail
cd "$(dirname "$0")"
soffice --headless --convert-to 'docx:MS Word 2007 XML' --outdir ../docx docx-sources/*.html

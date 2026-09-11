#!/bin/sh
set -eu

if [ ! -f "${GBRAIN_HOME}/.gbrain/config.json" ]; then
    bun run src/cli.ts init --non-interactive --no-embedding
else
    bun run src/cli.ts init --migrate-only
fi

exec bun run src/cli.ts serve --http --bind 0.0.0.0 --port 3131 --suppress-bootstrap-token

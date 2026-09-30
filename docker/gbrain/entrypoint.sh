#!/bin/sh
set -eu

: "${GBRAIN_EMBEDDING_MODEL:=openrouter:openai/text-embedding-3-small}"
: "${GBRAIN_EMBEDDING_DIMENSIONS:=1536}"
: "${GBRAIN_SYNTHESIS_MODEL:=openrouter:anthropic/claude-haiku-4.5}"
export GBRAIN_EMBEDDING_MODEL GBRAIN_EMBEDDING_DIMENSIONS GBRAIN_SYNTHESIS_MODEL

if [ ! -f "${GBRAIN_HOME}/.gbrain/config.json" ]; then
    bun run src/cli.ts init --non-interactive \
        --embedding-model "${GBRAIN_EMBEDDING_MODEL}" \
        --embedding-dimensions "${GBRAIN_EMBEDDING_DIMENSIONS}"
else
    bun run src/cli.ts init --migrate-only
fi

bun run src/cli.ts config set models.think "${GBRAIN_SYNTHESIS_MODEL}"

exec bun run src/cli.ts serve --http --bind 0.0.0.0 --port 3131 --suppress-bootstrap-token

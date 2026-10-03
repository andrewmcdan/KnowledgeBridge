#!/bin/sh
set -eu

# Compose passes unset keys as empty strings; remove them so gbrain sees only real credentials.
[ -n "${OPENROUTER_API_KEY:-}" ] || unset OPENROUTER_API_KEY
[ -n "${OPENAI_API_KEY:-}" ] || unset OPENAI_API_KEY

# Default models follow whichever provider key is available; OpenRouter wins when both are set.
# Both routes serve OpenAI text-embedding-3-small at 1536 dimensions, so switching the route keeps
# stored vectors comparable. Changing the embedding model or dimensions still requires migration.
if [ -n "${OPENROUTER_API_KEY:-}" ]; then
    default_embedding_model="openrouter:openai/text-embedding-3-small"
    default_synthesis_model="openrouter:anthropic/claude-haiku-4.5"
elif [ -n "${OPENAI_API_KEY:-}" ]; then
    default_embedding_model="openai:text-embedding-3-small"
    default_synthesis_model="openai:gpt-4o-mini"
else
    echo "Set OPENROUTER_API_KEY or OPENAI_API_KEY in the root .env file." >&2
    exit 1
fi

: "${GBRAIN_EMBEDDING_MODEL:=${default_embedding_model}}"
: "${GBRAIN_EMBEDDING_DIMENSIONS:=1536}"
: "${GBRAIN_SYNTHESIS_MODEL:=${default_synthesis_model}}"
export GBRAIN_EMBEDDING_MODEL GBRAIN_EMBEDDING_DIMENSIONS GBRAIN_SYNTHESIS_MODEL

# An explicit model must have its provider's key; otherwise gbrain would start and fail on first use.
require_key_for() {
    case "$1" in
        openrouter:*) [ -n "${OPENROUTER_API_KEY:-}" ] || missing="OPENROUTER_API_KEY" ;;
        openai:*) [ -n "${OPENAI_API_KEY:-}" ] || missing="OPENAI_API_KEY" ;;
        *) missing="" ;;
    esac
    if [ -n "${missing}" ]; then
        echo "Model $1 needs ${missing}; set it or clear the model override in .env." >&2
        exit 1
    fi
}
missing=""
require_key_for "${GBRAIN_EMBEDDING_MODEL}"
require_key_for "${GBRAIN_SYNTHESIS_MODEL}"
echo "gbrain models: embedding=${GBRAIN_EMBEDDING_MODEL} (${GBRAIN_EMBEDDING_DIMENSIONS}d) synthesis=${GBRAIN_SYNTHESIS_MODEL}"

if [ ! -f "${GBRAIN_HOME}/.gbrain/config.json" ]; then
    bun run src/cli.ts init --non-interactive \
        --embedding-model "${GBRAIN_EMBEDDING_MODEL}" \
        --embedding-dimensions "${GBRAIN_EMBEDDING_DIMENSIONS}"
else
    bun run src/cli.ts init --migrate-only
fi

bun run src/cli.ts config set models.think "${GBRAIN_SYNTHESIS_MODEL}"

exec bun run src/cli.ts serve --http --bind 0.0.0.0 --port 3131 --suppress-bootstrap-token

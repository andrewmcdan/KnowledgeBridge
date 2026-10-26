#!/usr/bin/env bash
# Bash equivalent of provision-gbrain.ps1: creates the knowledgebridge source if it is missing,
# registers a source- and slug-bound OAuth client, and stores its credentials in the ignored root .env.
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"
SOURCE_ID="knowledgebridge"
credential_prefix="GBRAIN_OAUTH"
client_name="knowledgebridge-backend"
binding_args=(--bound-slug-prefixes "$SOURCE_ID/")
if [[ "${1:-}" == "--extraction" ]]; then
  credential_prefix="GBRAIN_EXTRACTION_OAUTH"
  client_name="knowledgebridge-extraction"
  binding_args=()
elif [[ $# -gt 0 ]]; then
  echo "Usage: provision-gbrain.sh [--extraction]" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Root .env file is required. Copy .env.example first and set OPENROUTER_API_KEY or OPENAI_API_KEY." >&2
  exit 1
fi

cd "$PROJECT_ROOT"

gbrain_cli() {
  docker compose run --rm --no-deps -T --entrypoint bun gbrain run src/cli.ts "$@"
}

# Replaces or appends NAME=VALUE without passing the value to another process's argument list.
set_env_value() {
  local name="$1" value="$2" line replaced=false temporary
  temporary="$(mktemp "$PROJECT_ROOT/.env.XXXXXX")"
  chmod 600 "$temporary"
  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" == "$name="* ]]; then
      printf '%s=%s\n' "$name" "$value" >>"$temporary"
      replaced=true
    else
      printf '%s\n' "$line" >>"$temporary"
    fi
  done <"$ENV_FILE"
  if [[ "$replaced" == false ]]; then
    printf '%s=%s\n' "$name" "$value" >>"$temporary"
  fi
  mv "$temporary" "$ENV_FILE"
}

existing_client_id="$(sed -n "s/^${credential_prefix}_CLIENT_ID=//p" "$ENV_FILE" | tail -n 1 | tr -d '[:space:]')"

if ! gbrain_cli sources list --json 2>/dev/null | grep -q "\"id\"[[:space:]]*:[[:space:]]*\"$SOURCE_ID\""; then
  echo "Creating gbrain source '$SOURCE_ID'..."
  gbrain_cli sources add "$SOURCE_ID" \
    --path /data/knowledgebridge/knowledgebridge \
    --name KnowledgeBridge \
    --no-federated \
    --force >/dev/null
fi

if ! output="$(gbrain_cli auth register-client "$client_name" \
  --grant-types client_credentials \
  --scopes 'read write' \
  --source "$SOURCE_ID" \
  --federated-read "$SOURCE_ID" \
  "${binding_args[@]}" 2>&1)"; then
  # The registration output can contain the one-time secret, so print only the failure lines.
  echo "gbrain client registration failed:" >&2
  grep -iv 'secret' <<<"$output" >&2 || true
  exit 1
fi

client_id="$(sed -n 's/^[[:space:]]*Client ID:[[:space:]]*\([^[:space:]]*\)[[:space:]]*$/\1/p' <<<"$output" | head -n 1)"
client_secret="$(sed -n 's/^[[:space:]]*Client Secret:[[:space:]]*\([^[:space:]]*\)[[:space:]]*$/\1/p' <<<"$output" | head -n 1)"
if [[ -z "$client_id" || -z "$client_secret" ]]; then
  echo "gbrain registered a client, but its credential output could not be parsed." >&2
  exit 1
fi

set_env_value "${credential_prefix}_CLIENT_ID" "$client_id"
set_env_value "${credential_prefix}_CLIENT_SECRET" "$client_secret"
set_env_value GBRAIN_OAUTH_TOKEN_URL "http://localhost:${GBRAIN_PORT:-3131}/token"

if [[ -n "$existing_client_id" && "$existing_client_id" != "$client_id" ]]; then
  if ! gbrain_cli auth revoke-client "$existing_client_id" >/dev/null 2>&1; then
    echo "The new client works, but the previous client '$existing_client_id' could not be revoked. Revoke it manually." >&2
  fi
fi

echo "Provisioned a source-bound gbrain OAuth client and saved its credentials to the ignored root .env file."
echo "Client ID: $client_id"
echo "Client secret: <saved and redacted>"

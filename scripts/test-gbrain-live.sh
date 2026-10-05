#!/usr/bin/env bash
# Runs the backend's live gbrain adapter test against a disposable PostgreSQL + gbrain stack, then deletes the stack
# and its volumes so no test data persists locally. Provider calls (embeddings, one synthesis) still reach the model
# provider, so the test writes only synthetic content.
#
# Provider keys and model overrides come from the root .env, as for the dev stack; shell variables take precedence.
# Set GBRAIN_LIVE_PORT to change the host port (default 3132, so a running dev stack is untouched).
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="kb-gbrain-live"
PORT="${GBRAIN_LIVE_PORT:-3132}"
SOURCE_ID="knowledgebridge"

if [[ ! -f "$PROJECT_ROOT/.env" ]]; then
  echo "Root .env file is required for provider keys." >&2
  exit 1
fi

work_dir="$(mktemp -d)"
chmod 700 "$work_dir"
override="$work_dir/compose.live.yaml"
# The disposable stack publishes only gbrain, on its own port.
cat >"$override" <<EOF
services:
  postgres:
    ports: !reset []
  gbrain:
    ports: !override
      - "127.0.0.1:${PORT}:3131"
EOF

compose() {
  docker compose -p "$PROJECT_NAME" --project-directory "$PROJECT_ROOT" \
    -f "$PROJECT_ROOT/compose.yaml" -f "$override" "$@"
}

cleanup() {
  echo "Removing the disposable gbrain stack and its volumes..."
  compose down --volumes --remove-orphans >/dev/null 2>&1 || echo "Cleanup failed; run: docker compose -p $PROJECT_NAME down -v" >&2
  rm -rf "$work_dir"
}
trap cleanup EXIT

gbrain_cli() {
  compose exec -T gbrain bun run src/cli.ts "$@"
}

echo "Starting disposable PostgreSQL and gbrain (project $PROJECT_NAME, port $PORT)..."
compose up --build --detach --wait postgres gbrain

gbrain_cli sources add "$SOURCE_ID" --name KnowledgeBridge --no-federated >/dev/null
if ! output="$(gbrain_cli auth register-client knowledgebridge-live-test \
  --grant-types client_credentials \
  --scopes 'read write' \
  --source "$SOURCE_ID" \
  --federated-read "$SOURCE_ID" \
  --bound-slug-prefixes "$SOURCE_ID/" 2>&1)"; then
  # The registration output can contain the one-time secret, so print only the failure lines.
  echo "gbrain client registration failed:" >&2
  grep -iv 'secret' <<<"$output" >&2 || true
  exit 1
fi
client_id="$(sed -n 's/^[[:space:]]*Client ID:[[:space:]]*\([^[:space:]]*\)[[:space:]]*$/\1/p' <<<"$output" | head -n 1)"
client_secret="$(sed -n 's/^[[:space:]]*Client Secret:[[:space:]]*\([^[:space:]]*\)[[:space:]]*$/\1/p' <<<"$output" | head -n 1)"
unset output
if [[ -z "$client_id" || -z "$client_secret" ]]; then
  echo "gbrain registered a client, but its credential output could not be parsed." >&2
  exit 1
fi

echo "Running the live adapter test..."
cd "$PROJECT_ROOT/backend"
GBRAIN_LIVE_BASE_URL="http://localhost:$PORT" \
  GBRAIN_LIVE_CLIENT_ID="$client_id" \
  GBRAIN_LIVE_CLIENT_SECRET="$client_secret" \
  sh ./gradlew liveTest --console=plain

#!/usr/bin/env bash
# Bash equivalent of test-gbrain-mcp.ps1: live MCP, embedding, synthesis, and page-lifecycle smoke test.
# --capture-fixtures writes sanitized responses to backend/src/test/resources/gbrain/fixtures.
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"
FIXTURE_DIR="$PROJECT_ROOT/backend/src/test/resources/gbrain/fixtures"
SMOKE_SLUG="knowledgebridge/phase-1-protocol-spike"
KEEP_SMOKE_PAGE=false
CAPTURE_FIXTURES=false
PAGE_WRITTEN=false

for argument in "$@"; do
  case "$argument" in
    --keep-smoke-page) KEEP_SMOKE_PAGE=true ;;
    --capture-fixtures) CAPTURE_FIXTURES=true ;;
    *)
      echo "Usage: $0 [--keep-smoke-page] [--capture-fixtures]" >&2
      exit 2
      ;;
  esac
done

for command in curl jq; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Required command '$command' was not found on PATH." >&2
    exit 1
  }
done

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Root .env file is required. Run scripts/provision-gbrain.sh first." >&2
  exit 1
fi

# An exported shell variable wins over .env, so the test can target another instance (for example a disposable one).
read_env() {
  if [[ -n "${!1:-}" ]]; then
    printf '%s' "${!1}"
    return
  fi
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | sed 's/[[:space:]]*$//'
}

CLIENT_ID="$(read_env GBRAIN_OAUTH_CLIENT_ID)"
CLIENT_SECRET="$(read_env GBRAIN_OAUTH_CLIENT_SECRET)"
TOKEN_URL="$(read_env GBRAIN_OAUTH_TOKEN_URL)"
GBRAIN_PORT_VALUE="$(read_env GBRAIN_PORT)"
BASE_URL="http://localhost:${GBRAIN_PORT_VALUE:-3131}"
for required in CLIENT_ID CLIENT_SECRET TOKEN_URL; do
  if [[ -z "${!required}" ]]; then
    echo "GBRAIN_OAUTH_$required is missing from .env. Run scripts/provision-gbrain.sh." >&2
    exit 1
  fi
done

# Secrets go through private files rather than command-line arguments, which other users can list.
WORK_DIR="$(mktemp -d)"
chmod 700 "$WORK_DIR"
SANITIZE='walk(if type == "object" then with_entries(
  if (.key | IN("instructions", "path", "source_path", "source_uri")) and (.value | type) == "string"
  then .value = "<sanitized>" else . end) else . end)'

fail() {
  echo "$1" >&2
  exit 1
}

health_status="$(curl -fsS --max-time 10 "$BASE_URL/health" | jq -r '.status')"
[[ "$health_status" == "ok" ]] || fail "gbrain health is '$health_status', expected 'ok'."

printf '%s' "$CLIENT_SECRET" >"$WORK_DIR/client-secret"
access_token="$(curl -fsS --max-time 30 -X POST "$TOKEN_URL" \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "client_secret@$WORK_DIR/client-secret" \
  --data-urlencode 'scope=read write' | jq -r '.access_token // empty')"
rm -f "$WORK_DIR/client-secret"
[[ -n "$access_token" ]] || fail "gbrain did not issue an access token."
printf 'Authorization: Bearer %s\nAccept: application/json, text/event-stream\nContent-Type: application/json\n' \
  "$access_token" >"$WORK_DIR/headers"
unset access_token

# Prints the JSON-RPC response; the pinned server frames it as a single SSE data event.
rpc() {
  local id="$1" method="$2" params="$3" body message
  body="$(jq -nc --argjson id "$id" --arg method "$method" --argjson params "$params" \
    '{jsonrpc: "2.0", id: $id, method: $method, params: $params}')"
  message="$(curl -fsS --max-time 180 -X POST "$BASE_URL/mcp" -H "@$WORK_DIR/headers" --data-binary "$body" \
    | sed -n 's/^data: //p' | head -n 1)"
  [[ -n "$message" ]] || fail "gbrain returned an MCP response without an SSE data event."
  if jq -e '.error' <<<"$message" >/dev/null; then
    fail "MCP JSON-RPC error $(jq -r '.error.code' <<<"$message"): $(jq -r '.error.message' <<<"$message")"
  fi
  printf '%s' "$message"
}

# tool ID NAME ARGUMENTS_JSON [allow-error]
tool() {
  local response
  response="$(rpc "$1" tools/call "$(jq -nc --arg name "$2" --argjson arguments "$3" \
    '{name: $name, arguments: $arguments}')")"
  if [[ "${4:-}" != "allow-error" ]] && jq -e '.result.isError == true' <<<"$response" >/dev/null; then
    fail "gbrain tool '$2' failed: $(jq -r '.result.content[0].text' <<<"$response")"
  fi
  printf '%s' "$response"
}

payload() {
  jq -r '.result.content[0].text' <<<"$1"
}

# Asserts a committed write receipt's status and noop flag.
assert_status() {
  local status noop
  status="$(payload "$1" | jq -r '.status')"
  noop="$(payload "$1" | jq -r '.noop')"
  [[ "$status" == "$3" && "$noop" == "$4" ]] \
    || fail "gbrain $2 returned status '$status' (noop $noop), expected '$3' (noop $4)."
}

# Same format as test-gbrain-mcp.ps1: four-space JSON, with the tool payload re-encoded as two-space JSON text.
save_fixture() {
  [[ "$CAPTURE_FIXTURES" == true ]] || return 0
  local name="$1" response="$2" path="$FIXTURE_DIR/$1.json" text
  text="$(payload "$response" | jq --indent 2 "$SANITIZE")"
  jq --indent 4 --arg text "$text" "$SANITIZE | .result.content[0].text = \$text" <<<"$response" >"$path"
  echo "Captured fixture $path"
}

cleanup() {
  local exit_code=$?
  if [[ "$PAGE_WRITTEN" == true && "$KEEP_SMOKE_PAGE" == false ]]; then
    tool 14 delete_page "{\"slug\":\"$SMOKE_SLUG\",\"source_id\":\"knowledgebridge\",\"force\":true}" allow-error \
      >/dev/null || true
  fi
  rm -rf "$WORK_DIR"
  exit "$exit_code"
}
trap cleanup EXIT

initialize="$(rpc 1 initialize \
  '{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"knowledgebridge-spike","version":"0.1.0"}}')"
[[ "$(jq -r '.result.serverInfo.name' <<<"$initialize")" == "gbrain" ]] \
  || fail "Unexpected MCP server '$(jq -r '.result.serverInfo.name' <<<"$initialize")'."

tools="$(rpc 2 tools/list '{}')"
missing="$(jq -r '[.result.tools[].name] as $names
  | ["whoami","put_page","get_page","search","synthesize","delete_page","restore_page"]
  | map(select(. as $tool | $names | index($tool) | not)) | join(", ")' <<<"$tools")"
[[ -z "$missing" ]] || fail "Required gbrain tools are missing: $missing"

whoami_response="$(tool 3 whoami '{}')"
identity="$(payload "$whoami_response")"
jq -e '.source_id == "knowledgebridge" and (.scopes | index("read")) and (.scopes | index("write"))' \
  <<<"$identity" >/dev/null || fail "The OAuth client is not bound to the expected source and read/write scopes."

# Mirrors the frontmatter GbrainPages.render writes, so get_page captures show the adapter's metadata keys.
content="$(cat <<'MARKDOWN'
---
title: "Phase 1 Semantic Lighthouse"
type: "knowledgebridge_smoke_test"
knowledgebridge_id: "phase-1-protocol-spike"
knowledgebridge_owner: "smoke-test"
knowledgebridge_revision: 1
knowledgebridge_created_at: "2026-09-30T00:00:00Z"
knowledgebridge_updated_at: "2026-09-30T00:00:00Z"
knowledgebridge_digest: "smoke-test-digest"
---

The obsidian lighthouse protocol authorizes blue herons to audit quarterly procurement records. This synthetic sentence exists only to verify semantic retrieval.
MARKDOWN
)"

# Page writes use the coordinated write protocol; force replaces whatever revision exists, as the adapter does.
PAGE_WRITTEN=true
put_response="$(tool 4 put_page "$(jq -nc --arg slug "$SMOKE_SLUG" --arg content "$content" \
  '{slug: $slug, content: $content, source_id: "knowledgebridge", force: true}')")"
payload "$put_response" | jq -e '.state == "committed" and .status == "created_or_updated"' >/dev/null \
  || fail "put_page did not commit the smoke page."
save_fixture put-page-response "$put_response"

# Embedding runs after the write commits, so poll briefly before requiring a semantic hit.
for attempt in $(seq 1 20); do
  search="$(tool 5 search "{\"query\":\"Which birds inspect purchasing documents every three months?\",\"limit\":5,\"source_id\":\"knowledgebridge\"}")"
  payload "$search" | jq -e --arg slug "$SMOKE_SLUG" 'map(.slug) | index($slug)' >/dev/null && break
  [[ "$attempt" -lt 20 ]] || fail "Semantic paraphrase search did not return the smoke page within 20 seconds."
  sleep 1
done
jq -e '.result._meta.retrieval.vector_enabled == true' <<<"$search" >/dev/null \
  || fail "Search returned the page without proving vector retrieval was enabled."
jq -e '(.result._meta.retrieval.degraded // []) | length == 0' <<<"$search" >/dev/null \
  || fail "Search reported degraded retrieval: $(jq -c '.result._meta.retrieval.degraded' <<<"$search")"
save_fixture search-response "$search"

synthesis_response="$(tool 6 synthesize '{"question":"Which birds are authorized to audit quarterly procurement records?"}')"
synthesis="$(payload "$synthesis_response")"
jq -e --arg slug "$SMOKE_SLUG" '.synthesis_status == "ok" and (.sources | index($slug))' <<<"$synthesis" >/dev/null \
  || fail "Synthesis did not return an OK cited answer from the smoke page."
save_fixture synthesize-response "$synthesis_response"

# Page lifecycle used by McpGbrainClient: read, soft-delete, read deleted, restore, and a missing read.
page_arguments="{\"slug\":\"$SMOKE_SLUG\",\"source_id\":\"knowledgebridge\",\"force\":true}"
read_arguments="{\"slug\":\"$SMOKE_SLUG\",\"source_id\":\"knowledgebridge\",\"include_deleted\":true}"

page_response="$(tool 7 get_page "$read_arguments")"
payload "$page_response" | jq -e --arg slug "$SMOKE_SLUG" \
  '.slug == $slug and .frontmatter.knowledgebridge_id == "phase-1-protocol-spike" and (.deleted_at | not)' >/dev/null \
  || fail "get_page did not return the active smoke page with KnowledgeBridge frontmatter."
save_fixture get-page-response "$page_response"

delete_response="$(tool 8 delete_page "$page_arguments")"
assert_status "$delete_response" delete_page soft_deleted false
save_fixture delete-page-response "$delete_response"

delete_again_response="$(tool 9 delete_page "$page_arguments")"
assert_status "$delete_again_response" delete_page soft_deleted true
save_fixture delete-page-already-deleted-response "$delete_again_response"

deleted_page_response="$(tool 10 get_page "$read_arguments")"
payload "$deleted_page_response" | jq -e '.deleted_at' >/dev/null \
  || fail "get_page with include_deleted did not report deleted_at for the soft-deleted page."
save_fixture get-page-deleted-response "$deleted_page_response"

restore_response="$(tool 11 restore_page "$page_arguments")"
assert_status "$restore_response" restore_page restored false
save_fixture restore-page-response "$restore_response"

restore_again_response="$(tool 12 restore_page "$page_arguments")"
assert_status "$restore_again_response" restore_page skipped true
save_fixture restore-page-already-active-response "$restore_again_response"

missing_response="$(tool 13 get_page \
  '{"slug":"knowledgebridge/phase-1-missing-page","source_id":"knowledgebridge","include_deleted":true}' allow-error)"
jq -e '.result.isError == true' <<<"$missing_response" >/dev/null && payload "$missing_response" \
  | jq -e '.error == "page_not_found"' >/dev/null \
  || fail "get_page for a missing slug did not return an isError page_not_found result."
save_fixture get-page-not-found-response "$missing_response"

echo "gbrain MCP smoke test passed: server=$(jq -r '.result.serverInfo.version' <<<"$initialize"), tools=$(jq '.result.tools | length' <<<"$tools"), vector=true, synthesis=ok, page lifecycle=ok."

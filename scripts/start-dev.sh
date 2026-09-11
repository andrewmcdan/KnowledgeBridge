#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PID=""
FRONTEND_PID=""
DATABASE_STARTED=false
KEEP_DATABASE=false

if [[ "${1:-}" == "--keep-database" ]]; then
  KEEP_DATABASE=true
elif [[ -n "${1:-}" ]]; then
  echo "Usage: $0 [--keep-database]" >&2
  exit 2
fi

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM

  [[ -n "$FRONTEND_PID" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
  [[ -n "$BACKEND_PID" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  wait "$FRONTEND_PID" "$BACKEND_PID" 2>/dev/null || true

  if [[ "$DATABASE_STARTED" == true && "$KEEP_DATABASE" == false ]]; then
    echo "Stopping PostgreSQL/pgvector..."
    (cd "$PROJECT_ROOT" && docker compose stop postgres) || true
  fi

  exit "$exit_code"
}
trap cleanup EXIT INT TERM

for command in docker npm node java; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Required command '$command' was not found on PATH." >&2
    exit 1
  }
done

cd "$PROJECT_ROOT"

if docker compose ps --services --status running backend frontend | grep -q .; then
  echo "The Docker backend or frontend is already running. Run 'docker compose down' first." >&2
  exit 1
fi

echo "Starting PostgreSQL/pgvector..."
docker compose up -d postgres
DATABASE_STARTED=true

deadline=$((SECONDS + 60))
while (( SECONDS < deadline )); do
  container_id="$(docker compose ps -q postgres)"
  if [[ -n "$container_id" ]] && [[ "$(docker inspect --format '{{.State.Health.Status}}' "$container_id" 2>/dev/null)" == "healthy" ]]; then
    break
  fi
  sleep 2
done

if [[ -z "${container_id:-}" ]] || [[ "$(docker inspect --format '{{.State.Health.Status}}' "$container_id" 2>/dev/null)" != "healthy" ]]; then
  echo "PostgreSQL did not become healthy within 60 seconds." >&2
  exit 1
fi

if [[ ! -d "$PROJECT_ROOT/frontend/node_modules" ]]; then
  echo "Installing frontend dependencies..."
  (cd "$PROJECT_ROOT/frontend" && npm install)
fi

export SPRING_DOCKER_COMPOSE_ENABLED=false
# Use Compose's .env parser instead of sourcing configuration as shell code.
compose_json="$(docker compose config --format json)"
postgres_port="$(node -pe 'JSON.parse(require("fs").readFileSync(0,"utf8")).services.postgres.ports.find(p => p.target === 5432).published' <<< "$compose_json")"
postgres_database="$(node -pe 'JSON.parse(require("fs").readFileSync(0,"utf8")).services.postgres.environment.POSTGRES_DB' <<< "$compose_json")"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${postgres_port}/${postgres_database}"
SPRING_DATASOURCE_USERNAME="$(node -pe 'JSON.parse(require("fs").readFileSync(0,"utf8")).services.postgres.environment.POSTGRES_USER' <<< "$compose_json")"
SPRING_DATASOURCE_PASSWORD="$(node -pe 'JSON.parse(require("fs").readFileSync(0,"utf8")).services.postgres.environment.POSTGRES_PASSWORD' <<< "$compose_json")"
export SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
unset compose_json

echo "Starting Spring Boot and Vite..."
(cd "$PROJECT_ROOT/backend" && ./gradlew bootRun --no-daemon --console=plain) &
BACKEND_PID=$!
(cd "$PROJECT_ROOT/frontend" && npm run dev) &
FRONTEND_PID=$!

echo
echo "KnowledgeBridge development services are starting:"
echo "  Frontend: http://localhost:5173"
echo "  Backend:  http://localhost:8080/api/health"
echo "Press Ctrl+C to stop the local application processes."

wait -n "$BACKEND_PID" "$FRONTEND_PID"

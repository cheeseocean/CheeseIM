#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.e2e.yml"
SERVER_LOG="${TMPDIR:-/tmp}/cheeseim-e2e-server.log"

export CHEESEIM_AUTH_JWT_SECRET="${CHEESEIM_AUTH_JWT_SECRET:-local-jwt-secret-at-least-32-bytes}"
export CHEESEIM_LOGIN_ASSERTION_ENABLED=true
export CHEESEIM_LOGIN_ASSERTION_SECRET="${CHEESEIM_LOGIN_ASSERTION_SECRET:-local-integration-secret-at-least-32-bytes}"

server_pid=""
cleanup() {
  if [[ -n "${server_pid}" ]]; then
    kill "${server_pid}" 2>/dev/null || true
    wait "${server_pid}" 2>/dev/null || true
  fi
  if [[ "${CHEESEIM_E2E_KEEP_MIDDLEWARE:-0}" != "1" ]]; then
    docker compose -f "${COMPOSE_FILE}" down >/dev/null
  fi
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null || {
  echo "docker is required to run the CheeseBox E2E environment" >&2
  exit 1
}
command -v go >/dev/null || {
  echo "Go 1.24.2 or newer is required to run the CheeseBox E2E test" >&2
  exit 1
}

docker compose -f "${COMPOSE_FILE}" up -d --wait
docker compose -f "${COMPOSE_FILE}" exec -T mongo mongosh --quiet --eval \
  'try { rs.status().ok } catch (error) { rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]}) }'

for _ in $(seq 1 30); do
  if docker compose -f "${COMPOSE_FILE}" exec -T mongo mongosh --quiet --eval \
    'quit(db.hello().isWritablePrimary ? 0 : 1)' >/dev/null; then
    break
  fi
  sleep 1
done
docker compose -f "${COMPOSE_FILE}" exec -T mongo mongosh --quiet --eval \
  'if (!db.hello().isWritablePrimary) { throw new Error("Mongo replica set did not elect a primary") }'

(
  cd "${REPO_ROOT}/server"
  ./gradlew :bootstrap-all:bootRun \
    --args='--server.port=18079 --spring.application.name=cheese-im-all-in-one'
) >"${SERVER_LOG}" 2>&1 &
server_pid=$!

for _ in $(seq 1 60); do
  if curl --silent --output /dev/null http://127.0.0.1:18079/api/auth/login; then
    break
  fi
  if ! kill -0 "${server_pid}" 2>/dev/null; then
    echo "CheeseIM server exited before becoming healthy; log follows:" >&2
    tail -n 200 "${SERVER_LOG}" >&2
    exit 1
  fi
  sleep 1
done
curl --silent --output /dev/null http://127.0.0.1:18079/api/auth/login || {
  echo "CheeseIM HTTP API did not become ready; log follows:" >&2
  tail -n 200 "${SERVER_LOG}" >&2
  exit 1
}

cd "${REPO_ROOT}/sdks/go"
CHEESEIM_E2E=1 go test ./integration -v -count=1

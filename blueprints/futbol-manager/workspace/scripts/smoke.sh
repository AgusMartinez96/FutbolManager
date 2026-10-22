#!/usr/bin/env bash
# Boots the packaged jar against the compose database and checks HTTP responses.
#
# Usage: bash scripts/smoke.sh [PATH EXPECTED_SUBSTRING]...
#   Always checks GET /api/health -> 200 containing "status":"UP".
#   Each extra PATH/EXPECTED pair must return 200 and contain EXPECTED (fixed string).
# Env:   SMOKE_PORT (default 8080), SPRING_PROFILES_ACTIVE (passed through to the jar).
#
# Runs from the project root so the jar resolves .env (spring.config.import uses the cwd).
# Exits 0 only when every check passes; the app is stopped on every exit path.
set -euo pipefail
cd "$(dirname "$0")/.."

test -f target/futbol-manager.jar
docker compose up -d --wait db >/dev/null

port="${SMOKE_PORT:-8080}"
PORT="$port" java -jar target/futbol-manager.jar >target/smoke-app.log 2>&1 &
pid=$!

cleanup() {
  kill "$pid" 2>/dev/null || true   # app may already be gone; stopping it is the goal
  wait "$pid" 2>/dev/null || true   # java exits 143 on SIGTERM; that is the expected outcome
}
trap cleanup EXIT

if ! curl -s -o /dev/null --retry 45 --retry-delay 2 --retry-all-errors "http://localhost:$port/api/health"; then
  echo "smoke: app never answered on port $port; last log lines:" >&2
  tail -n 40 target/smoke-app.log >&2
  exit 1
fi

check() {
  local path="$1" expected="$2" response code body
  response="$(curl -s -w '\n%{http_code}' "http://localhost:$port$path")"
  code="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [ "$code" != "200" ]; then
    echo "smoke: GET $path returned $code, expected 200" >&2
    exit 1
  fi
  if ! grep -qF -- "$expected" <<<"$body"; then
    echo "smoke: GET $path body does not contain: $expected" >&2
    exit 1
  fi
  echo "smoke: GET $path -> 200 ok"
}

check /api/health '"status":"UP"'
while [ "$#" -ge 2 ]; do
  check "$1" "$2"
  shift 2
done
echo "smoke OK"

#!/usr/bin/env bash
# Runs the image built by `docker build -t futbol-manager:local .` with the prod profile against
# the compose database and checks health, non-root user and that Swagger is closed (404).
#
# Usage: bash scripts/docker-smoke.sh      (exits 0 only when every check passes)
set -euo pipefail
cd "$(dirname "$0")/.."

# Load DB_NAME / DB_USERNAME / DB_PASSWORD from .env for this shell only.
set -a
. ./.env
set +a

docker compose up -d --wait db >/dev/null
docker rm -f fm-smoke >/dev/null 2>&1 || true   # a leftover container from an aborted run is expected sometimes

# Inside the container "localhost" is the container itself; the compose db is reached through the
# host on 5433. --add-host makes host.docker.internal resolve on Linux CI as well.
docker run -d --name fm-smoke \
  --add-host=host.docker.internal:host-gateway \
  -p 8081:8080 \
  --env-file .env \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e "DB_URL=jdbc:postgresql://host.docker.internal:5433/${DB_NAME}" \
  futbol-manager:local >/dev/null

cleanup() {
  docker rm -f fm-smoke >/dev/null 2>&1 || true   # removing the container is the goal either way
}
trap cleanup EXIT

if ! curl -s -o /dev/null --retry 60 --retry-delay 2 --retry-all-errors http://localhost:8081/api/health; then
  echo "docker-smoke: container never answered; last log lines:" >&2
  docker logs --tail 60 fm-smoke >&2
  exit 1
fi

health="$(curl -s http://localhost:8081/api/health)"
grep -qF '"status":"UP"' <<<"$health"

uid="$(docker exec fm-smoke id -u)"
if [ "$uid" = "0" ]; then
  echo "docker-smoke: container runs as root" >&2
  exit 1
fi

for path in /v3/api-docs /swagger-ui.html; do
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8081$path")"
  if [ "$code" != "404" ]; then
    echo "docker-smoke: GET $path returned $code, expected 404 in prod" >&2
    exit 1
  fi
done
echo "docker-smoke OK"

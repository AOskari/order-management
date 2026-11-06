#!/bin/sh
#
# Starts the local development environment: PostgreSQL, Keycloak and the application.
#   - creates .env from .env.example when missing
#   - starts the containers and waits for their health checks
#
# PostgreSQL roles and the Keycloak realm are created by the containers themselves
# (docker/local/postgres/init and the realm import), so nothing runs after 'up'.
#
# Safe to run repeatedly. Run it from anywhere; paths resolve from the repository root.
#
# Usage: sh scripts/local/init-local-env.sh [--reset]
#   --reset   remove the containers and the database volume first (destroys local data)

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
cd "$repo_root"

usage() {
    sed -n '2,13p' "$script_dir/$(basename -- "$0")" | sed 's/^# \{0,1\}//'
}

log() { printf '[init] %s\n' "$*"; }
die() {
    printf '[init] ERROR: %s\n' "$*" >&2
    exit 1
}

reset=false
for argument in "$@"; do
    case "$argument" in
        --reset) reset=true ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            printf '[init] ERROR: unknown option: %s\n' "$argument" >&2
            usage
            exit 2
            ;;
    esac
done

command -v docker >/dev/null 2>&1 || die "docker is required"
docker compose version >/dev/null 2>&1 || die "the docker compose plugin is required"
docker info >/dev/null 2>&1 || die "the Docker daemon is not running"

if [ ! -f .env ]; then
    cp .env.example .env
    log "created .env from .env.example"
fi

# Windows editors write CRLF; the .env import and the scripts do not tolerate a stray \r.
if ! tr -d '\r' <.env | cmp -s - .env; then
    tr -d '\r' <.env >.env.tmp
    mv .env.tmp .env
    log "converted .env to LF line endings"
fi

if [ "$reset" = true ]; then
    log "removing containers and the database volume"
    docker compose down -v
fi

log "starting PostgreSQL, Keycloak and the application"
docker compose up -d --wait --wait-timeout 300 ||
    die "the containers did not become healthy — inspect 'docker compose logs'"

app_port=$(sed -n 's/^APP_PORT=//p' .env | head -n 1 | tr -d '\r')
app_port=${APP_PORT:-${app_port:-8080}}
log "application is available at http://localhost:$app_port/api/v1/orders"
log "token: sh scripts/local/get-token.sh -u customer1 -p password"

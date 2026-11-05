#!/bin/sh
#
# Initializes the local development infrastructure:
#   - creates .env from .env.example when missing
#   - starts PostgreSQL and Keycloak and waits for their health checks
#   - applies the oms_owner / oms_app roles and grants (docker/local/postgres/init/01-roles.sh)
#   - verifies database logins and the Keycloak realm
#
# Safe to run repeatedly. Run it from anywhere; paths resolve from the repository root.
#
# Usage: sh scripts/init-local-env.sh [--reset]
#   --reset   remove the containers and the database volume first (destroys local data)

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
cd "$repo_root"

usage() {
    sed -n '2,12p' "$script_dir/$(basename -- "$0")" | sed 's/^# \{0,1\}//'
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

if tr -d '\r' <"$repo_root/.env" | cmp -s - "$repo_root/.env"; then
    :
else
    tr -d '\r' <"$repo_root/.env" >"$repo_root/.env.tmp"
    mv "$repo_root/.env.tmp" "$repo_root/.env"
    log "converted .env to LF line endings"
fi

set -a
# shellcheck disable=SC1091
. "$repo_root/.env"
set +a

for variable in DB_URL DB_APP_USER DB_APP_PASSWORD DB_OWNER_USER DB_OWNER_PASSWORD OIDC_ISSUER_URI; do
    eval "value=\${$variable:-}"
    if [ -n "$value" ]; then
        continue
    fi
    default=$(sed -n "s/^$variable=//p" .env.example | head -n 1)
    [ -n "$default" ] || die ".env is missing $variable and .env.example has no value for it"
    printf '%s=%s\n' "$variable" "$default" >>.env
    eval "export $variable=\"\$default\""
    log "added $variable to .env from .env.example"
done

if [ "$reset" = true ]; then
    log "removing containers and the database volume"
    docker compose down -v
fi

log "starting PostgreSQL and Keycloak"
docker compose up -d --wait --wait-timeout 240 ||
    die "containers did not become healthy — inspect 'docker compose logs'"

db_name=$(docker compose exec -T postgres printenv POSTGRES_DB)

log "applying database roles and grants"
docker compose exec -T postgres sh -s <docker/local/postgres/init/01-roles.sh >/dev/null

verify_login() {
    role=$1
    password=$2
    docker compose exec -T -e PGPASSWORD="$password" postgres \
        psql -h 127.0.0.1 -U "$role" -d "$db_name" -tAc 'select 1' >/dev/null 2>&1 ||
        die "$role cannot log in to $db_name with the password from .env"
    log "$role can log in to $db_name"
}

verify_login "$DB_OWNER_USER" "$DB_OWNER_PASSWORD"
verify_login "$DB_APP_USER" "$DB_APP_PASSWORD"

if command -v curl >/dev/null 2>&1; then
    issuer=${OIDC_ISSUER_URI:-http://localhost:8081/realms/order-management}
    curl -fsS -o /dev/null "$issuer" ||
        die "Keycloak realm is not available at $issuer — try 'sh scripts/init-local-env.sh --reset'"
    log "Keycloak realm is available at $issuer"
fi

log "local infrastructure is ready; start the application with ./mvnw spring-boot:run"

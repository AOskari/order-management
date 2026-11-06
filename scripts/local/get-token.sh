#!/bin/sh
#
# Fetches an access token from the local Keycloak realm with the resource owner password
# grant. The token is printed on stdout; diagnostics go to stderr, so the script can be
# used in a command substitution.
#
# Usage: sh scripts/local/get-token.sh -u customer1 -p password [--issuer URL] [--json]
#   -u, --username NAME   realm user (customer1, customer2, support1, admin1)
#   -p, --password PASS   password of that user
#   --issuer URL          realm URL; defaults to OIDC_ISSUER_URI, then .env, then
#                         http://host.docker.internal:8081/realms/order-management
#   --json                print the whole token response instead of the access token

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)

die() {
    printf 'get-token: ERROR: %s\n' "$*" >&2
    exit 1
}

usage() {
    sed -n '2,14p' "$script_dir/$(basename -- "$0")" | sed 's/^# \{0,1\}//'
}

username=""
password=""
issuer=""
json=false

while [ $# -gt 0 ]; do
    case "$1" in
        -u | --username)
            [ $# -ge 2 ] || die "$1 requires a value"
            username=$2
            shift 2
            ;;
        -p | --password)
            [ $# -ge 2 ] || die "$1 requires a value"
            password=$2
            shift 2
            ;;
        --issuer)
            [ $# -ge 2 ] || die "--issuer requires a value"
            issuer=$2
            shift 2
            ;;
        --json)
            json=true
            shift
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            die "unknown option: $1"
            ;;
    esac
done

[ -n "$username" ] || die "-u/--username is required"
[ -n "$password" ] || die "-p/--password is required"

command -v curl >/dev/null 2>&1 || die "curl is required"

if [ -z "$issuer" ]; then
    issuer=${OIDC_ISSUER_URI:-}
fi
if [ -z "$issuer" ] && [ -f "$repo_root/.env" ]; then
    issuer=$(sed -n 's/^OIDC_ISSUER_URI=//p' "$repo_root/.env" | head -n 1 | tr -d '\r')
fi
[ -n "$issuer" ] || issuer=http://host.docker.internal:8081/realms/order-management

response=$(curl -sS -w '\n%{http_code}' -X POST "$issuer/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'client_id=order-management-api' \
    --data-urlencode 'scope=openid' \
    --data-urlencode "username=$username" \
    --data-urlencode "password=$password") ||
    die "cannot reach $issuer"

status=$(printf '%s' "$response" | tail -n 1)
body=$(printf '%s' "$response" | sed '$d')

if [ "$status" != "200" ]; then
    detail=$(printf '%s' "$body" | sed -n 's/.*"error_description":"\([^"]*\)".*/\1/p')
    [ -n "$detail" ] || detail=$body
    die "token request failed (HTTP $status): $detail"
fi

if [ "$json" = true ]; then
    printf '%s\n' "$body"
    exit 0
fi

token=$(printf '%s' "$body" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$token" ] || die "no access_token in the response"

expires=$(printf '%s' "$body" | sed -n 's/.*"expires_in":\([0-9]*\).*/\1/p')

subject=""
if command -v openssl >/dev/null 2>&1; then
    payload=$(printf '%s' "$token" | cut -d . -f2)
    case $((${#payload} % 4)) in
        2) payload="$payload==" ;;
        3) payload="$payload=" ;;
    esac
    claims=$(printf '%s' "$payload" | sed -e 's/-/+/g' -e 's/_/\//g' |
        openssl base64 -d -A 2>/dev/null || true)
    subject=$(printf '%s' "$claims" | sed -n 's/.*"sub":"\([^"]*\)".*/\1/p')
fi

if [ -n "$subject" ]; then
    printf 'token for %s (customer %s, valid %ss)\n' "$username" "$subject" "${expires:-?}" >&2
else
    printf 'token for %s (valid %ss)\n' "$username" "${expires:-?}" >&2
fi

printf '%s\n' "$token"

# Order Management Service

A small Spring Boot REST API for creating and managing orders. PostgreSQL stores the
orders, Keycloak issues the JWTs, and Spring Security enforces role and ownership rules.

- **CUSTOMER** creates orders and reads their own.
- **SUPPORT** reads orders across customers and moves them to `CONFIRMED` and `SHIPPED`.
- **ADMIN** reads all orders and delivers them; it may perform any status change.
- Totals are computed on the server; the caller never supplies one.

Single currency. No payments, inventory or product catalog.

## Prerequisites

Docker with Compose and `curl`. Java 17 is only needed for the Maven path; the Maven
wrapper is included.

## Configure and run locally

Everything, including the application, runs in containers:

```bash
cp .env.example .env               # optional for Compose, required by spring-boot:run
docker compose up -d               # PostgreSQL, Keycloak and the API on :8080
```

One command is enough: `up` builds the `app` image from the current source
(`pull_policy: build`), waits for the database and the realm through the healthchecks, then
starts the API. Only the first build is slow, because Maven resolves the dependencies
inside the image.

`docker compose up` is also the initialization: the PostgreSQL image runs
`docker/local/postgres/init/01-roles.sh` when it creates the data directory, and Keycloak
imports `realm-export.json` on start. No helper script runs afterwards. `sh
scripts/local/init-local-env.sh` brings up the same stack and waits for the health checks
(`--reset` recreates the database volume).

To run the application from Maven instead, start only the infrastructure and let `.env`
configure it:

```bash
docker compose up -d postgres keycloak           # infrastructure only
./mvnw spring-boot:run                           # .env is loaded automatically
```

`.env` is imported by `application-local.yaml`, which the `local` default profile
activates, and by `springboot4-dotenv`; `application.yaml` has no fallbacks, so a missing
variable fails fast. The `app` container skips that import (`SPRING_PROFILES_ACTIVE=docker`)
and receives every value from the compose environment.

Keycloak advertises `http://host.docker.internal:8081` as its issuer (`KEYCLOAK_URL` in
`.env`), not `localhost`. That name resolves to the Docker host from the host and from the
containers, so a token requested on the host carries an issuer the `app` container accepts.

Get a token and call the API:

```bash
TOKEN=$(sh scripts/local/get-token.sh -u support1 -p password)    # token on stdout, summary on stderr
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/orders
```

The scripts exist for both shells: `scripts/local/init-local-env.ps1` and
`scripts/local/get-token.ps1 -Username customer1 -Password password`. For an API client, use the
same values with the password grant: token URL
`http://host.docker.internal:8081/realms/order-management/protocol/openid-connect/token`,
client `order-management-api` (public, no secret, credentials in the body), scope `openid`.

Local users in the imported realm, all with password `password`: `customer1`, `customer2`
(CUSTOMER), `support1` (SUPPORT), `admin1` (ADMIN); they are defined in
`docker/local/keycloak/realm-export.json`. Tokens last 5 minutes. The password grant is
enabled for local testing only: it needs no browser round trip, whereas authorization code
would require a redirect URI and a callback listener.

## Configuration

| Variable                              | Purpose                                           |
| ------------------------------------- | ------------------------------------------------- |
| `DB_URL`                              | JDBC URL, used by the datasource and Flyway       |
| `DB_APP_USER` / `DB_APP_PASSWORD`     | Runtime database role                             |
| `DB_OWNER_USER` / `DB_OWNER_PASSWORD` | Flyway role                                       |
| `OIDC_ISSUER_URI`                     | Keycloak realm issuer, must match the `iss` claim |
| `KEYCLOAK_URL`                        | Compose only: the base URL Keycloak advertises    |

`DB_PORT` and `APP_PORT` (Compose only) publish PostgreSQL and the API on `5432` and
`8080`. If something else already listens on `8080` — an IDE run, for example — set
`APP_PORT` so the container can bind. Set `SPRING_PROFILES_ACTIVE` to skip the `.env`
import and supply the variables through the environment instead, which is what the `app`
container does.

The realm, its users and the direct access grant are development fixtures — **not
production authentication**. A real deployment uses a confidential client against the
organisation's identity provider.

## Product data

The service is downstream of a product management service and does not depend on it: the
caller fetches the product first and submits the details with the order. Each item stores
that snapshot, so historical orders stay stable when the product changes.

| Field         | Values                                      |
| ------------- | ------------------------------------------- |
| `productId`   | External identifier, for example `PROD-101` |
| `productName` | Display name, for example `Keyboard`        |
| `priceUnit`   | `PIECE`, `KILOGRAM` or `LITER`              |
| `unitPrice`   | Decimal with at most two places             |

Orders can be created while the product service is down, because nothing is fetched at
runtime.

## API examples

All paths are under `/api/v1`. Use a token for a user with the role listed.

| Method | Path                                    | Roles                        |
| ------ | --------------------------------------- | ---------------------------- |
| POST   | `/api/v1/orders`                        | CUSTOMER                     |
| GET    | `/api/v1/orders/{id}`                   | CUSTOMER own, SUPPORT, ADMIN |
| GET    | `/api/v1/customers/{customerId}/orders` | CUSTOMER own, SUPPORT, ADMIN |
| GET    | `/api/v1/orders`                        | SUPPORT, ADMIN               |
| PATCH  | `/api/v1/orders/{id}/status`            | SUPPORT, ADMIN (see below)   |

Create an order:

```bash
curl -X POST localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":"PROD-101","productName":"Keyboard",
       "priceUnit":"PIECE","quantity":2,"unitPrice":49.99}]}'
```

Returns `201` with a `Location` header and the computed total.

List and update:

```bash
curl 'localhost:8080/api/v1/customers/{customerId}/orders?page=0&size=20&sortFields=CREATED_AT&sortOrder=DESC' \
  -H "Authorization: Bearer $TOKEN"

curl -X PATCH localhost:8080/api/v1/orders/{id}/status \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"CONFIRMED"}'
```

A CUSTOMER may only pass their own ID (the JWT `sub`); other customers and orders return
`403`, missing orders `404`, invalid transitions `409`.

Status updates follow `CREATED → CONFIRMED → SHIPPED → DELIVERED`: SUPPORT may set
`CONFIRMED` and `SHIPPED`, while only ADMIN may set `DELIVERED`. Repeating the current
status is an idempotent `200`; skipping a step returns `409`; a SUPPORT request for
`DELIVERED` returns `403`.

`sortFields` accepts `CREATED_AT`, `UPDATED_AT` and `TOTAL_AMOUNT` (default
`CREATED_AT DESC`); `size` is 1–100. Errors are `application/problem+json`, with an
`errors` map for validation failures.

## Running the tests

```bash
./mvnw test        # unit and slice tests
./mvnw verify      # adds the Testcontainers integration tests (PostgreSQL + Keycloak)
```

Integration tests skip when Docker is unavailable.

## CI pipeline

`.github/workflows/ci.yml` validates `docker-compose.yaml`, runs `./mvnw -B verify` on
Java 17 and builds the container image. To validate locally:

```bash
docker compose config -q && ./mvnw -B verify && docker build -t order-management:local .
```

## Container image

The `Dockerfile` is two-stage: the Maven wrapper builds the jar, then the jar alone runs on
`eclipse-temurin:17-jre` as an unprivileged `app` user. Configuration comes from the same
environment variables as a local run; nothing is baked into the image.

Compose runs the whole stack, including the application. The `app` service reaches
PostgreSQL as `postgres:5432` and validates tokens against `http://host.docker.internal:8081`,
the issuer Keycloak advertises. That hostname is a local-development detail: a real
deployment points `OIDC_ISSUER_URI` at the organisation's identity provider instead.

## Deployment in a real environment

Implemented: the image, Compose for the local stack, and a CI job that validates, tests and
builds. Nothing under `docker/local/` is deployed, and a real environment does not use the
`app` service: production uses a managed database and the organisation's identity provider.

A real pipeline would publish the image under the commit SHA, read secrets from the
platform store, roll the new version into a test environment, gate on readiness and keep
the previous image digest for rollback. Migrations ship with the application: the new
version applies them at startup, and if a migration fails the rollout stops and the
previous version keeps serving, so migrations must stay backward compatible for one
release. Readiness gating needs `spring-boot-starter-actuator`, which is not included
yet.

## Assumptions, limitations and known issues

- **SUPPORT confirms and ships; only ADMIN delivers.** The scenario gives SUPPORT status
  updates, so SUPPORT may target `CONFIRMED` and `SHIPPED`; `DELIVERED` is reserved for
  ADMIN, which can also perform the earlier steps.
- **Transitions are enforced in the application**, not by a database trigger.
- **No row-level security.** The runtime role is not the owner and has no `BYPASSRLS`.
- **Prices are trusted from the caller.** A cached product check is the planned mitigation.
- **No optimistic locking or idempotency keys**, so a retried `POST` duplicates an order.
- **Single currency**; no inventory, tax, discounts, shipping or payment logic.
- **Docker state lives in two named volumes** (`pgdata`, `keycloak-data`), so recreating a
  container keeps the data and `docker compose down -v` (or `--reset`) removes it. The roles
  and the realm are created only for an empty state, so a change to `realm-export.json`
  needs that reset; the realm pins its user IDs, so even a re-import keeps the token
  subjects that order rows reference.

## Project layout

```
src/main/java/com/example/ordermanagement
├── api             controllers, DTOs, mapper, pagination, error handling
├── domain          status model, services, exceptions
├── persistence     JPA entities and repositories
├── security        JWT roles and current-user access
└── config          security and JPA configuration
```

Migrations are in `src/main/resources/db/migration`, the local stack in
`docker-compose.yaml` and `docker/local/`, and the local helper scripts in `scripts/local/`.

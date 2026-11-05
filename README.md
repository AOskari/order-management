# Order Management Service

A small Spring Boot REST API for creating and managing orders. PostgreSQL stores the
orders, Keycloak issues the JWTs, and Spring Security enforces role and ownership rules.

- **CUSTOMER** creates orders and reads their own.
- **SUPPORT** reads orders across customers.
- **ADMIN** reads all orders and advances status: `CREATED → CONFIRMED → SHIPPED → DELIVERED`.
- Totals are computed on the server; the caller never supplies one.

Single currency. No payments, inventory or product catalog.

## Prerequisites

Java 17 or later, Docker with Compose, and `curl`. The Maven wrapper is included.

## Configure and run locally

```bash
cp .env.example .env           # local values; keep the file LF encoded
docker compose up -d           # PostgreSQL and Keycloak
./mvnw spring-boot:run         # .env is loaded automatically
```

`sh scripts/init-local-env.sh` performs the first two steps, applies the database roles
and verifies both the database logins and the Keycloak realm (`--reset` recreates the
volume). `.env` is imported by `application-local.yaml`, which the `local` default profile
activates, and by `springboot4-dotenv`; `application.yaml` has no fallbacks, so a missing
variable fails fast.

Get a token and call the API:

```bash
TOKEN=$(sh scripts/get-token.sh -u support1 -p password)    # token on stdout, summary on stderr
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/orders
```

The scripts exist for both shells: `scripts/init-local-env.ps1` and
`scripts/get-token.ps1 -Username customer1 -Password password`. For an API client, use the
same values with the password grant: token URL
`http://localhost:8081/realms/order-management/protocol/openid-connect/token`, client
`order-management-api` (public, no secret, credentials in the body), scope `openid`.

Local users in the imported realm, all with password `password`: `customer1`, `customer2`
(CUSTOMER), `support1` (SUPPORT), `admin1` (ADMIN). Tokens last 5 minutes. The password
grant is enabled for local testing only: it needs no browser round trip, whereas
authorization code would require a redirect URI and a callback listener.

## Configuration

| Variable                              | Purpose                                     |
| ------------------------------------- | ------------------------------------------- |
| `DB_URL`                              | JDBC URL, used by the datasource and Flyway |
| `DB_APP_USER` / `DB_APP_PASSWORD`     | Runtime database role                       |
| `DB_OWNER_USER` / `DB_OWNER_PASSWORD` | Flyway role                                 |
| `OIDC_ISSUER_URI`                     | Keycloak realm issuer                       |

`DB_PORT` (Compose only) publishes PostgreSQL on `5432`. Set `SPRING_PROFILES_ACTIVE` to
skip the `.env` import and supply the variables through the environment instead.

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
| PATCH  | `/api/v1/orders/{id}/status`            | ADMIN                        |

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
`403`, missing orders `404`, invalid transitions `409`. `sortFields` accepts `CREATED_AT`,
`UPDATED_AT` and `TOTAL_AMOUNT` (default `CREATED_AT DESC`); `size` is 1–100. Errors are
`application/problem+json`, with an `errors` map for validation failures.

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

Compose starts only PostgreSQL and Keycloak because the service validates the token
issuer, so a containerised application would have to reach Keycloak under the exact issuer
URL in the token.

## Deployment in a real environment

Implemented: the image, Compose for local infrastructure, and a CI job that validates,
tests and builds. Nothing under `docker/local/` is deployed; production uses a managed
database and the organisation's identity provider.

A real pipeline would publish the image under the commit SHA, read secrets from the
platform store, roll the new version into a test environment, gate on readiness and keep
the previous image digest for rollback. Migrations ship with the application: the new
version applies them at startup, and if a migration fails the rollout stops and the
previous version keeps serving, so migrations must stay backward compatible for one
release. Readiness gating needs `spring-boot-starter-actuator`, which is not included
yet.

## Assumptions, limitations and known issues

- **Only ADMIN updates status.** The specification contradicts itself about SUPPORT; this
  follows the explicit status-update instruction, so SUPPORT is read-only.
- **Transitions are enforced in the application**, not by a database trigger.
- **No row-level security.** The runtime role is not the owner and has no `BYPASSRLS`.
- **Prices are trusted from the caller.** A cached product check is the planned mitigation.
- **No optimistic locking or idempotency keys**, so a retried `POST` duplicates an order.
- **Single currency**; no inventory, tax, discounts, shipping or payment logic.
- **Docker initialization is one-shot**: `docker compose down -v`, or `--reset`, recreates it.

## Project layout

```
src/main/java/com/example/ordermanagement
├── api             controllers, DTOs, mapper, pagination, error handling
├── domain          status model, services, exceptions
├── persistence     JPA entities and repositories
├── security        JWT roles and current-user access
└── config          security and JPA configuration
```

Migrations are in `src/main/resources/db/migration`, local infrastructure in
`docker-compose.yaml` and `docker/local/`, and helper scripts in `scripts/`.

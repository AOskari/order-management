# Local development infrastructure

Everything in this directory exists only to run the service on a developer machine:

- `postgres/init/01-roles.sh` creates the `oms_owner` (migrations) and `oms_app` (runtime)
  roles on first start of the PostgreSQL container.
- `keycloak/realm-export.json` is the development realm: roles, the `order-management-api`
  client with direct access grants, and the seeded test users.

Both are applied by `docker compose up` itself: the PostgreSQL entrypoint runs the role
script when it initializes the data directory, and Keycloak imports the realm on start. No
helper script runs afterwards.

Both stores are named volumes (`pgdata`, `keycloak-data`), so recreating a container keeps
the data. The realm import only runs while the realm does not exist yet, so changing
`realm-export.json` requires dropping the volume: `docker compose down -v`. The users pin
their `id` in the export, so even a fresh import keeps the same token `subject`; orders
store that subject as `customer_id` and never lose their owner.

Neither is used in a real environment. A deployment runs against a managed PostgreSQL
instance (roles and grants provisioned by the platform) and the organisation's identity
provider (real clients, real users, no password grant). Only `docker-compose.yaml` mounts
these files, and only locally and in the Testcontainers integration tests.

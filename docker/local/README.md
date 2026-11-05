# Local development infrastructure

Everything in this directory exists only to run the service on a developer machine:

- `postgres/init/01-roles.sh` creates the `oms_owner` (migrations) and `oms_app` (runtime)
  roles on first start of the PostgreSQL container.
- `keycloak/realm-export.json` is the development realm: roles, the `order-management-api`
  client with direct access grants, and the seeded test users.

Neither is used in a real environment. A deployment runs against a managed PostgreSQL
instance (roles and grants provisioned by the platform) and the organisation's identity
provider (real clients, real users, no password grant). Only `docker-compose.yaml` mounts
these files, and only locally and in the Testcontainers integration tests.

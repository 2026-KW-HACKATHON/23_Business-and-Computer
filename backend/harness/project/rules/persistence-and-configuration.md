# Persistence and Configuration

## Goal

Protect PostgreSQL data and deployment configuration from implicit schema changes, hard-coded secrets, and unverified persistence assumptions.

## Repo Facts

- `build.gradle` includes Spring Data JPA and the PostgreSQL JDBC driver.
- `src/main/resources/application.yaml` reads `DATABASE_URL` and five HikariCP settings from environment variables.
- No entities, repositories, transactions, datasource credentials, JPA DDL mode, SQL scripts, migration tool, or migration directory are present.
- `src/main/resources/application-local.*` and `.env*` files are ignored, except `.env.example`.
- The project uses Java 21, Spring Boot 4.1.1, and Gradle wrapper 9.7.1.

## Core Rules

- Keep environment-specific datasource values and all secrets outside tracked application configuration.
- Preserve the existing environment-variable contract unless a configuration change explicitly includes deployment compatibility.
- Do not enable `ddl-auto` schema mutation, add startup DDL, or assume Hibernate-generated schema is the production migration strategy.
- Before the first schema change, make an explicit migration-tool and ownership decision; no stable migration entry point currently exists.
- Treat entity mappings, column nullability, uniqueness, relationships, fetch/cascade behavior, and transaction boundaries as data-contract decisions, not incidental annotations.
- Do not create physical `FOREIGN KEY` constraints between domain tables. Validate referenced IDs in the application service before creating or updating records.
- When deleting a referenced record, handle dependent records in the application service within the same transaction according to the domain's deletion policy. Verify missing-reference and deletion behavior with focused tests when those write flows are added.
- Continue to use `NOT NULL`, `UNIQUE`, and other database constraints where the data contract requires them; the no-physical-FK policy does not remove those constraints.
- Do not add a dependency when the existing Spring Boot stack can meet the requirement directly; keep versions managed by Spring unless there is a verified compatibility reason.
- Avoid logging datasource URLs when they may contain credentials or other sensitive connection information.

## Design Checklist

- Does the change alter persisted data, schema, indexes, constraints, or transaction boundaries?
- How will existing data be migrated or remain compatible?
- Which configuration values differ by environment, and where are they supplied?
- Are nullability and uniqueness rules domain requirements or guesses?
- Does a dependency change remain compatible with Java 21, Spring Boot 4.1.1, and Gradle 9.7.1?

## Implementation Checklist

- Secrets and machine-specific values are absent from tracked files.
- Every required environment variable is identified in the change handoff.
- Persistence changes have repository/integration coverage appropriate to their behavior.
- Transactions are applied at the operation boundary rather than used to hide unclear write ordering.
- Schema-affecting changes include an explicit migration approach; if none is available, the change is reported as unverified/blocked rather than applied implicitly.
- Dependency changes are minimal and the Gradle build resolves successfully.

## Common Smells

- Hard-coding a local PostgreSQL URL, username, or password in `application.yaml`.
- Enabling Hibernate schema creation/update to avoid choosing a migration strategy.
- Adding entity relationships with broad cascade or eager fetching by default.
- Assuming an in-memory database behaves identically to PostgreSQL.
- Committing local configuration or secrets because the application context otherwise fails to start.


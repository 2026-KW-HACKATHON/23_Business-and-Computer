# Testing and Verification

## Goal

Make backend changes verifiable at the narrowest useful level while ensuring security, API, and PostgreSQL-specific behavior receive appropriate integration coverage.

## Repo Facts

- `src/test/java/com/gakkum/backend/BackendApplicationTests.java` contains one `@SpringBootTest` context smoke test.
- `build.gradle` enables JUnit Platform and includes Spring Boot test starters for JPA, validation, and Spring MVC.
- No unit-test convention, MVC slice test, repository test, test profile, test datasource, Testcontainers setup, fixtures, or coverage threshold exists.
- Loading the full application context requires the datasource environment variables declared in `src/main/resources/application.yaml` and may require a reachable PostgreSQL database.

## Core Rules

- Add focused tests for changed behavior; do not rely on the context smoke test as proof of API, domain, security, or persistence correctness.
- Give every JUnit test method a Korean `@DisplayName` that states the behavior it verifies.
- Prefer the narrowest test scope that exercises the contract: plain unit tests for domain behavior, MVC/security tests for HTTP boundaries, and database-backed tests for JPA/PostgreSQL behavior.
- Test both allowed and rejected paths for validation and authorization changes.
- Do not silently replace PostgreSQL with a different database for behavior that depends on SQL dialect, constraints, locking, or transaction semantics.
- Keep tests deterministic and isolated; do not depend on developer-local state, committed secrets, execution order, or external frontend behavior.
- If required datasource infrastructure or environment variables are unavailable, report the exact unverified test rather than weakening production configuration to make it pass.
- On any failure, follow `harness/core/rules/test-failure-triage.md` before changing code or tests.

## Design Checklist

- What regression or contract would fail before the change and pass afterward?
- Can the behavior be verified without loading the full Spring context?
- Does the test require real PostgreSQL semantics?
- Which unauthenticated, unauthorized, invalid, not-found, conflict, and success paths are relevant?
- What configuration or infrastructure must be present to reproduce the test?

## Implementation Checklist

- New behavior has focused assertions, including negative paths where applicable.
- Security tests verify both the intended public/protected boundary and HTTP status.
- Persistence tests verify constraints and transaction behavior against a suitable database.
- Tests do not expose credentials or require untracked local files without documentation.
- Run `./gradlew test` when the environment supports it; record failures, missing prerequisites, and any narrower verification performed.

## Common Smells

- Adding only another `contextLoads()` test for a feature.
- Using `@SpringBootTest` for pure business logic with no Spring dependency.
- Mocking away the authorization rule being tested.
- Switching production configuration to an embedded database solely to satisfy tests.
- Deleting or loosening a failing assertion without confirming whether it represents a real contract.


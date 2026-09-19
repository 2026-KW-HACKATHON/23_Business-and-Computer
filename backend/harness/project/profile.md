# Project Profile

## Stack

- Java 21
- Spring Boot 4.1.1 with Spring MVC, Spring Security, Bean Validation, and Spring Data JPA
- Gradle wrapper 9.7.1 with Groovy build scripts
- PostgreSQL through the PostgreSQL JDBC driver and HikariCP datasource settings
- Lombok for compile-time code generation
- JUnit Platform and Spring Boot test starters

## Repository Shape

```text
build.gradle                                      Gradle plugins and dependencies
settings.gradle                                   Single Gradle project definition
gradle/wrapper/                                   Pinned Gradle wrapper
src/main/java/com/gakkum/backend/                 Application base package
src/main/java/com/gakkum/backend/BackendApplication.java  Boot entry point
src/main/java/com/gakkum/backend/config/          Cross-cutting Spring configuration
src/main/resources/application.yaml               Runtime datasource configuration
src/test/java/com/gakkum/backend/                 Backend tests
harness/core/                                     Shared agent workflow (submodule)
harness/project/                                  Repository-specific agent guidance
```

The repository contains only the backend application. The frontend is a separate sibling project and is outside this backend's implementation scope unless a task explicitly requests a coordinated change.

## Product Chain Map

1. Application startup: `BackendApplication` starts Spring Boot and scans `com.gakkum.backend`.
2. HTTP security: `SecurityConfig` applies CORS, permits all `OPTIONS` requests and `/api/auth/**`, requires authentication for every other request, and returns HTTP 401 for unauthenticated access.
3. Persistence bootstrap: `application.yaml` reads datasource and HikariCP values from environment variables; Spring Data JPA connects through the PostgreSQL driver.
4. Business/domain request chain: no stable entry point found. There are currently no controllers, services, domain models, repositories, or business rules in the repository.

## Module Placement

| Code or artifact | Placement |
| --- | --- |
| Application bootstrap | `src/main/java/com/gakkum/backend/BackendApplication.java` |
| Global Spring Security and CORS configuration | `src/main/java/com/gakkum/backend/config/` |
| Runtime application configuration | `src/main/resources/application.yaml` |
| Tests | Mirror the production base package under `src/test/java/com/gakkum/backend/` |
| New API, application, domain, or persistence code | Keep it under `src/main/java/com/gakkum/backend/` and use feature-based packages; keep controller, service, repository, and entity responsibilities separated within each feature |
| Database migrations | no stable entry point found; no migration tool or migration directory is configured |
| Frontend code | Not in this repository; do not add frontend assets or frontend application code here |

## High-Risk Changes

- `src/main/java/com/gakkum/backend/config/SecurityConfig.java`: changes can expose protected endpoints, alter browser access, or change unauthenticated responses.
- `src/main/resources/application.yaml`: changes affect PostgreSQL connectivity, pool behavior, and secret/configuration handling.
- `build.gradle` and `gradle/wrapper/gradle-wrapper.properties`: changes affect the Java/Spring toolchain and dependency compatibility.
- `src/main/java/com/gakkum/backend/BackendApplication.java`: package or annotation changes can alter component/entity/repository scanning.
- Any future controller DTO, route, authentication behavior, entity mapping, or schema change: these create public or persistent contracts where none are currently established.

## Active Rules

| Rule | Applies when |
| --- | --- |
| `harness/project/rules/architecture-and-domain.md` | Adding or moving backend modules, choosing packages, or implementing business behavior |
| `harness/project/rules/dto-entity-mapping.md` | Adding or changing Request/Response DTO, Entity, or Converter mappings |
| `harness/project/rules/naming-convention.md` | Adding or renaming Java classes, methods, variables, DTOs, database tables, or columns |
| `harness/project/rules/api-and-security.md` | Adding/changing routes, DTOs, validation, authentication, authorization, error behavior, or CORS |
| `harness/project/rules/persistence-and-configuration.md` | Changing entities, repositories, schema, datasource settings, environment variables, or dependencies |
| `harness/project/rules/testing-and-verification.md` | Implementing or reviewing any behavior change and selecting verification scope |

## Reading Sets

| Task type | Read these project rules |
| --- | --- |
| New backend feature or domain behavior | `architecture-and-domain.md`, `naming-convention.md`, `api-and-security.md`, `persistence-and-configuration.md`, `testing-and-verification.md` as applicable to the touched layers; `dto-entity-mapping.md` when mappings are added |
| API or authentication bug/change | `naming-convention.md`, `api-and-security.md`, `testing-and-verification.md`; `dto-entity-mapping.md` when mappings change |
| DTO/Entity mapping or Converter refactor | `dto-entity-mapping.md`, `architecture-and-domain.md`, `naming-convention.md`, `testing-and-verification.md` |
| Database or configuration change | `naming-convention.md`, `persistence-and-configuration.md`, `testing-and-verification.md`; also `architecture-and-domain.md` if domain persistence is introduced |
| Refactor or package move | `architecture-and-domain.md`, `naming-convention.md`, `testing-and-verification.md`; add the other rule covering any changed contract |
| Dependency or Spring Boot configuration change | `persistence-and-configuration.md`, `api-and-security.md` when security/web behavior is affected, and `testing-and-verification.md` |

## Project Hard Constraints

- This repository is backend-only. Do not add or modify frontend implementation unless the task explicitly expands scope to the separate frontend project.
- Keep Spring-managed application code under `com.gakkum.backend` unless component scanning is deliberately updated and verified.
- Preserve the current default-deny HTTP boundary: only `OPTIONS /**` and `/api/auth/**` are public; every other request requires authentication unless a public-contract and security change is explicitly approved.
- Do not silently broaden CORS origins, public matchers, or authorization rules. Treat them as security and cross-application contracts.
- Keep datasource and pool values externalized. Never commit credentials, tokens, or environment-specific secrets.
- No database migration or schema-management convention exists. Do not enable implicit schema mutation or introduce ad hoc production DDL without an explicit migration decision.
- No stable domain model, API envelope, exception schema, or route-versioning policy exists. Use the feature-based package convention defined in `harness/project/rules/architecture-and-domain.md` without inventing other unstated conventions.
- Changes to routes, request/response shapes, status codes, authentication behavior, entity mappings, or persisted schema require focused tests and explicit compatibility consideration.

## Product Overview

This project is the backend server for "가꿈".

가꿈 is a local collaboration platform that connects small business owners in Wolgye 1-dong with university students from Kwangwoon University.

The platform addresses the gap between local businesses that need digital transformation support and students who need practical project experience.

Local business owners often need support such as:
- website development
- menu and poster design
- SNS marketing
- foreign language translation
- digital service improvements

However, finding and managing external freelancers can require significant time, cost, and communication effort. In addition, external workers may lack understanding of the local customer environment.

가꿈 allows nearby university students, who are both local residents and actual customers of these businesses, to propose improvements from a customer's perspective and perform related tasks.

The main workflow is:

1. Business owners create requests using simplified templates.
2. Verified Kwangwoon University students review requests and submit proposals based on their skills and portfolios.
3. Other students can provide feedback on proposals to validate whether improvements match customer needs.
4. Business owners select students and proceed with the project through a secure transaction process.
5. Completed projects and reviews accumulate as student portfolios.

The platform is limited to the Kwangwoon University and Wolgye 1-dong community to establish trust, enable direct collaboration, and create practical opportunities for students while supporting local business digitalization.

Core domains include:
- User authentication and authorization
- Student profiles and portfolios
- Business owner profiles
- Service requests
- Student proposals/applications
- Project contracts
- Secure payments
- Reviews and feedback

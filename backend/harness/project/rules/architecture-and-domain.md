# Architecture and Domain Rules

## Package Structure

Place domain-specific classes under `com.gakkum.backend.domain.<domain>.<layer>`.
Do not place classes directly in `com.gakkum.backend.domain.<domain>`.
Use the layer that matches the class's responsibility, such as `entity`, `repository`, `service`, `controller`, or `dto`.
Keep enums used only by an entity in that domain's `entity` package.
Use a common `<Domain>Controller` and `<Domain>Service` for related operations in a domain.
When adding a feature to an existing domain, extend those classes instead of creating a Controller or Service per feature.
When adding a new domain, create domain-level Controller and Service classes for its related operations.

Example:

```text
com.gakkum.backend.domain
└── user
    ├── entity
    │   ├── User.java
    │   └── UserRole.java
    └── repository
        └── UserRepository.java
```

## Layer Responsibility

Controller:

- Handle HTTP request/response
- Validate input
- Do not contain business logic

Service:

- Implement business rules
- Manage transaction boundaries

Repository:

- Handle persistence access

Entity:

- Represent domain state
- Do not expose directly through API

## Domain Rules

- Do not create generic modules without clear responsibility.
- New domains require explanation of responsibility and relationship.
- Avoid premature abstraction.

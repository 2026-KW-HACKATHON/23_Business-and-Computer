# Architecture and Domain Rules

## Package Structure

Use feature-based packaging.

Example:

```text
com.gakkum.backend
├── auth
├── user
├── business
├── request
├── application
├── payment
└── global
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

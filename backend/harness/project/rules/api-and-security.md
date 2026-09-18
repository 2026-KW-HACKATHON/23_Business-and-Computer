# API and Security Rules

## API Design

- Use RESTful API design.
- Use DTO instead of exposing Entity.
- Request and Response DTO must be separated.
- API changes require compatibility consideration.

## Exception Handling

- Use GlobalExceptionHandler.
- Do not return raw exceptions.
- Maintain consistent error response format.

## Authentication

- Authentication logic belongs to auth domain.
- Do not bypass SecurityConfig.
- Do not change public endpoints without explicit approval.

## CORS

- Do not add wildcard origins.
- CORS changes require security review.

# API and Security Rules

## API Design

- Use RESTful API design.
- Use DTO instead of exposing Entity.
- Request and Response DTO must be separated.
- All application controller endpoints must return `ResponseEntity<ApiResponse<T>>`; JWT-related controllers are exempt.
- File download exception: the success response of `POST /jobs/submissions/download` is an `application/zip` stream (`ResponseEntity<StreamingResponseBody>`), not `ApiResponse`. Errors raised before the transfer starts still use `ApiResponse`. Do not add other non-`ApiResponse` responses without stating them here.
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

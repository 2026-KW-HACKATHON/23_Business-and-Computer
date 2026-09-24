# DTO and Entity Mapping Rules

## Goal

Keep simple mappings close to their DTOs without moving business decisions into DTOs or creating a converter for every type.

## Mapping Ownership

When the controller and service use separate DTOs, keep each boundary conversion in the following location:

| Conversion | Owner |
| --- | --- |
| Request → Command | Request's `toCommand()` |
| Simple Entity → Result | Result's `from(entity)` |
| Result → Response | Response's `from(result)` |
| Entity creation with domain rules | Entity's `create(...)` |
| Combining multiple objects | Feature-specific `Assembler` or `Converter` |
| Queries, authorization, and state decisions | Service |
| All simple DTO conversions | Do not create a separate Converter |

`Command` and `Result` are service-layer DTOs, not converters. A factory method may use a builder internally, but callers should use the factory instead of repeating field-by-field builder mapping.

## Rules

- A Request DTO may depend on its service-layer Command through `toCommand()`. A Command must not depend on a controller-layer Request DTO.
- For new request-driven service operations, the controller passes `request.toCommand(...)` to the service rather than passing request fields separately. Include authenticated user context as a `toCommand(...)` argument when the service needs it.
- New application Request and Response DTOs use classes with private builders and public factory methods, following the existing DTO pattern. Do not introduce Java records for new application DTOs; existing record DTOs can remain until their own flow is changed.
- A Response DTO may depend on its service-layer Result through `from(result)`. A Result must not depend on a controller-layer Response DTO.
- When no Command boundary is used, Request DTO → Entity may use `toEntity()` only if the DTO contains every value needed for a direct conversion and no business decision or external context is required. Otherwise, the application service gathers the values and calls an Entity constructor or factory. An Entity must not depend on a DTO.
- When no Result boundary is used, Entity → Response DTO may use a static `from(entity)` factory on the Response DTO if mapping is a straightforward field copy. Do not add a separate Converter for this case.
- When one response combines multiple Entities or mapping logic is reused and no longer simple, place the pure mapping in a feature-specific Assembler or Converter. An Assembler or Converter must not query repositories or make business decisions.
- Keep business calculations and decisions in a Service or Facade. Use a Converter afterward only if assembling the result warrants one.
- For Entity → Entity, prefer the target Entity's constructor or factory, a domain method, or an application service according to the operation. Add a Converter only for a repeated, purely structural transformation between distinct Entity types; do not make it the default.

## User Example

Creating `User` needs a Kakao user ID and a role decided by the application flow. The service supplies those values to the `User` constructor; a Request DTO does not decide the role or implement this conversion in `toEntity()`.

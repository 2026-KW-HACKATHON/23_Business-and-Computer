# DTO and Entity Mapping Rules

## Goal

Keep simple mappings close to their DTOs without moving business decisions into DTOs or creating a converter for every type.

## Rules

- For Request DTO → Entity, use `toEntity()` only when the DTO contains every value needed for a direct conversion and no business decision or external context is required. Otherwise, the application service gathers the values and calls an Entity constructor or factory. An Entity must not depend on a DTO.
- For Entity → Response DTO, use a static `from(entity)` factory on the Response DTO when mapping is a straightforward field copy. Do not add a separate Converter for this case.
- When one response combines multiple Entities or mapping logic is reused and no longer simple, place the pure mapping in a feature-specific Converter. A Converter must not query repositories or make business decisions.
- Keep business calculations and decisions in a Service or Facade. Use a Converter afterward only if assembling the result warrants one.
- For Entity → Entity, prefer the target Entity's constructor or factory, a domain method, or an application service according to the operation. Add a Converter only for a repeated, purely structural transformation between distinct Entity types; do not make it the default.

## User Example

Creating `User` needs a Kakao user ID and a role decided by the application flow. The service supplies those values to the `User` constructor; a Request DTO does not decide the role or implement this conversion in `toEntity()`.

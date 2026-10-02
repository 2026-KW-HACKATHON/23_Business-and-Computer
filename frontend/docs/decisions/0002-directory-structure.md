# 0002. Hybrid directory structure for `src`

## Status

Accepted

## Context

The frontend `src` was the flat Vite starter (`main.tsx`, `App.tsx`, `assets`,
root-level CSS). No convention said where new product code, screens, hooks, or
backend API calls should live, so growth would drift into an ad-hoc layout.

## Decision

Adopt a **hybrid** layout for `src`: reusable code organized by layer
(`components`, `hooks`, `lib`, `api`, `types`, `styles`, `pages`) and domain
code grouped by feature (`features/<feature>/` with its own components, hooks,
api, types, and an `index.ts` public entry). The full tree and the placement,
boundary, and import rules live in `docs/conventions/directory-structure.md`,
and the enforced summary is in `AGENTS.md`.

## Rationale

- Small enough to start with today, but scales: domain growth lands inside a
  feature folder instead of bloating shared layers.
- Feature `index.ts` boundaries keep modules decoupled and make later
  mechanical enforcement (ESLint import rules) straightforward.
- Routing backend calls through `api` keeps network concerns out of components,
  which matters once the app starts calling the Spring Boot backend.

## Alternatives Considered

- Pure layer-based (only `components`/`pages`/`hooks`/`api`/`utils`): simpler,
  but shared folders grow unbounded as domains multiply.
- Pure feature-based (everything under `features`): high cohesion but heavier
  upfront ceremony than this greenfield app needs.

## Agent Guidance

- Apply the layout to **new** code; do not force-move existing starter files
  (that is a separate optional refactor).
- Do not add path aliases without a new decision record.
- If a feature boundary or import-depth rule is repeatedly crossed, add an
  ESLint rule and note it in `docs/conventions/coding.md`.

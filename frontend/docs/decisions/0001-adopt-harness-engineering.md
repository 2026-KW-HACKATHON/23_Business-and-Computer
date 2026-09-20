# 0001. Adopt harness engineering for the frontend

## Status

Accepted

## Context

The 가꿈 monorepo hosts a Spring Boot backend (`../backend`) and this React 19 +
TypeScript + Vite frontend. The backend already runs its own gate-based harness
under `../backend/harness/`. The frontend had no agent-facing rules, no knowledge store,
and no local drift checks, so agent work had no documented completion gate.

The `harness-starter-kit/` reference clone (with a dedicated `react` profile)
was available in this directory.

## Decision

Adopt a **frontend-local, independent** harness derived from the
harness-starter-kit `react` profile:

- `AGENTS.md` as the agent entry point.
- `.harness/structure-rules.json` + `.harness/source.json` for machine-readable
  rules and provenance.
- `scripts/check_structure.py` and `scripts/check_docs_drift.py` as local drift
  checks.
- `docs/conventions/`, `docs/decisions/`, `docs/failures/`, and `docs/domain/`
  as the knowledge store.
- `npm run check` as the normal completion gate: structure check → ESLint →
  TypeScript + Vite build.

## Rationale

- Keeps `frontend` and `backend` harnesses independent, as required; each stays
  self-contained and independently upgradable.
- Reuses the purpose-built `react` profile instead of inventing rules.
- Matches the completion gate already described in `README.md`.
- Adds only automatable, fast, local checks; avoids copying the kit's heavier
  effectiveness/decision/failure enforcement scripts before the app has real
  product flows.

## Alternatives Considered

- Mirror the backend's gate-based `../backend/harness/` layout: rejected because the user
  asked to reference the harness-starter-kit, and the react profile maps cleanly
  onto this stack.
- Copy the entire starter kit (CI workflow, all Python enforcement scripts):
  rejected as blind copying; the README states there is no frontend CI yet.

## Agent Guidance

- Run `npm run check` before reporting completion.
- Treat `harness-starter-kit/` as read-only reference; do not edit or commit it.
- When a new stack piece is added (router, state lib, test runner), revisit
  the `react` profile at https://github.com/harnessworks/harness-starter-kit
  and record the choice here.

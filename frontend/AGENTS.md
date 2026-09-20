# AGENTS.md

Agent entry point for the **가꿈 frontend**. This harness is independent from
`../backend`, which has its own instructions in `../backend/AGENTS.md`. Do not
apply backend rules here or frontend rules there.

## Project Overview

- Name: 가꿈 frontend
- Harness profile: `react` (from `harness-starter-kit/templates/profiles/react/`)
- Stack: React 19, TypeScript (strict, bundler mode), Vite 8, ESLint 10 flat
  config. Package manager: npm (`package-lock.json`).
- Status: currently the Vite starter UI. Product flows and backend API calls
  have not been added yet.

## Completion Gate

`npm run check` is the normal completion gate. Run it before reporting done:

```sh
npm run check
```

It runs, in order:

1. `python3 scripts/check_structure.py` — flags drift-prone files and any
   tracked local/generated files (`dist/`, `node_modules/`, `*.local`, the
   `harness-starter-kit/` clone).
2. `npm run lint` — ESLint including `react-hooks` rules.
3. `npm run build` — `tsc -b` type check + Vite production build.

Other useful scripts:

- `npm run dev` — local dev server.
- `npm run typecheck` — `tsc -b` without building.
- `npm run check:docs` — `python3 scripts/check_docs_drift.py`, run it after
  editing `AGENTS.md`, `README.md`, or anything under `docs/`.

Use `python3` (not `python`) — it is the available interpreter.

There is no automated browser test or frontend CI workflow yet. Changes to user
flows should also be verified in the browser; API changes require a running
backend and a check against its contract.

## Core Rules

- Preserve the existing architecture, tools, npm package manager, naming, and
  conventions. Do not add a new package manager, framework, router, or state
  library when existing tools solve the problem.
- Keep changes scoped to the requested behavior; prefer nearby patterns and
  existing helpers over new abstractions.
- Do not overwrite, delete, or move existing files unless the task requires it
  and the reason is clear.
- Do not edit or commit `dist/`, `node_modules/`, `.vite/`, `*.tsbuildinfo`, or
  the local `harness-starter-kit/` reference clone (all git-ignored).
- Do not leave `temp_`, `_new`, `_old`, `_backup`, or `_fix` files behind.

## Directory And Architecture Rules

- Application code lives in `src/`; `src/main.tsx` is the entry, `src/App.tsx`
  the root component. Component-imported assets go in `src/assets/`; root-served
  static files go in `public/`.
- Config: `vite.config.ts`, `eslint.config.js`, `tsconfig*.json`.
- Tests: none yet. When added, prefer Vitest + React Testing Library, colocate
  `*.test.tsx`, and wire the runner into `npm run check`.
- Generated/ignored (never edit): `dist/`, `node_modules/`, `.vite/`.
- Coding conventions that lint/tsc do not enforce live in
  `docs/conventions/coding.md` — read it before writing components.

## Knowledge Store

Before architectural, domain, workflow, or integration changes, inspect:

- `docs/decisions/` — accepted decisions (ADRs).
- `docs/failures/` — bug paths and rejected approaches not to repeat.
- `docs/conventions/coding.md` — React/TS/Vite conventions.
- `docs/domain/glossary.md` — domain terms (backend is the source of truth).

Add or update durable docs when behavior, architecture, commands, conventions,
or known failures change. If a non-trivial code change updates no `docs/` file,
explain why in the final report.

Handle **decision memory** explicitly when a change alters user workflow, input
contract or semantics, state normalization, API request/response shape, fallback
policy, routing, rendering strategy, or displayed decision criteria: add or
update `docs/decisions/*.md`, cite the ADR that already covers it, or explain
why the change is too narrow to need one.

Record a **failure note** in `docs/failures/*.md` when you fix a user-visible
runtime failure or high-risk bug path that should not recur (crash, security or
permission bug, data-loss risk, failed check, repeated agent mistake, or
cross-environment mismatch), unless it was purely transient or already covered.
Name the check — regression test, lint rule, drift check, or manual review
point — that prevents or detects recurrence, or explain why none is practical.

## Project Analysis Rule

When asked to analyze, review, summarize, onboard to, or explain this project,
inspect first when present: `README.md`, `AGENTS.md`, `.harness/source.json`,
`docs/conventions/`, `docs/decisions/`, `docs/failures/`, `docs/domain/`,
`scripts/check_structure.py`, `scripts/check_docs_drift.py`. Then summarize
structure, current behavior, tests, docs, known decisions, known failures, drift
checks, and recommended next work.

## Profile Guidance

When a new stack piece is introduced (router, state manager, styling system,
test runner, data-fetching layer), review the react profile reference at
`harness-starter-kit/templates/profiles/react/`. Adopt, adapt, skip, or defer
its snippets based on this repo's actual tools, then record the choice in a
decision record and report it.

## Commit And PR Rules

- Follow the repository's existing branch and commit conventions. Recent history
  uses Conventional Commit prefixes (`feat:`, `test:`, `chore:`, `fix:`,
  `docs:`, `refactor:`); keep each commit focused on one logical change.
- Before committing, inspect `git status` and the staged diff. Do not commit
  generated files, dependency directories, local env files, secrets, or the
  `harness-starter-kit/` clone.
- Run `npm run check` before committing. If a check cannot run, say why in the
  final report or PR notes.
- PRs should summarize changed files, checks run, assumptions, remaining risks,
  and manual follow-up.

## Completion Criteria

Before reporting completion:

- Run `npm run check` (and `npm run check:docs` if docs changed).
- Add or update tests for behavior changes once a test runner exists.
- Update `docs/` when behavior, architecture, commands, conventions, or known
  failures changed; handle decision/failure memory per the Knowledge Store rules
  above, or explain why none was needed.
- Confirm no temporary or generated files were left behind or staged.
- Summarize changed files, verification performed, and remaining risks.

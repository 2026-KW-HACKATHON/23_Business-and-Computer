# AGENTS.md

This file is the main entry point for the AI/agent development workflow.

## TL;DR (read this first)

Output these gates around your work:

1. **Task type** on its own line (bug / feature / refactor / UI / cross-module)
2. **Scope gate** — what we're solving, the approach, boundary, risk, how to verify
3. **Solution gate** — target behavior, chosen solution, surface change, compatibility
4. *(Long-running only)* **Plan gate** — operations workspace and current work package
5. Implement the change
6. **Build gate** — what was actually changed, any deviation from Scope/Solution
7. **Close gate** — verification record, unverified items, risk, final result

Gates are process records by default. Keep going unless there's a real blocker, or Solution gate exposes an unapproved public behavior decision (CLI/API/output/config/user-flow/entry-point semantics).

For tiny single-file changes that match Scope exactly, Build gate can collapse to one line.

---

- Process layer: `harness/core/` (generic, independently upgradable)
- Project layer: `harness/project/` (current project configuration)

## Standard Workflow

```
Scope → Solution → [Plan] → Build → Close
```

1. Declare task type
2. Output Scope gate
3. Output Solution gate
4. *(Long-running only)* Output Plan gate, create operations workspace
5. Read relevant rules (`harness/core/rules/` + `harness/project/rules/`)
6. Implement
7. Output Build gate
8. Verify; output Close gate

## Auto Trigger

| Keywords | Template |
| --- | --- |
| fix / bug / error / issue | `bug-fix` |
| refactor / optimize / clean up | `refactor` |
| add / create / implement | `new-feature` |
| adjust / modify / restyle | `ui-adjustment` |
| cross-module / affects multiple | `cross-module-change` |
| overall structure / directory restructure / workspace / migration | `cross-module-change` + long-running handling |

Automatically enters the workflow when keywords are detected; no user prompt needed.

## Autopilot

- Gates are process records by default; execute continuously when unblocked and no unapproved public behavior decision remains
- Only pause when user authorization is needed, existing changes could be damaged, or critical input is missing
- Pause after Solution gate when it changes public contracts, command/output/config semantics, user workflow, or entry-point behavior that the user has not already approved
- UI redesign / major visual overhaul: wait for user confirmation after Scope and Solution gates
- Long-running tasks: after Solution gate, create a todo/checklist first, then proceed in order
- Never pause with "if you agree / shall I continue" style prompts

## Concise Output

- Open with 1 sentence stating the goal
- Gates use multi-line short lists
- No status updates during execution by default; 1 sentence sync at key checkpoints
- Close gate: result → verified → unverified → risk

## Hard Constraints

- All changes, no matter how small, must go through the required gates for their tier
- Never skip the workflow and jump straight to implementation
- Never format intermediate progress as Close gate
- Auto-advance by default; do not ask questions in place of actions you can complete yourself
- Do not run builds or start dev servers by default (unless required for verification)
- On test failure, first execute `harness/core/rules/test-failure-triage.md`

### Project Hard Constraints

- This repository is backend-only. Do not add or modify frontend implementation unless the task explicitly expands scope to the separate frontend project.
- Keep Spring-managed application code under `com.gakkum.backend` unless component scanning is deliberately updated and verified.
- Preserve the current default-deny HTTP boundary: only `OPTIONS /**` and `/api/auth/**` are public; every other request requires authentication unless a public-contract and security change is explicitly approved.
- Do not silently broaden CORS origins, public matchers, or authorization rules. Treat them as security and cross-application contracts.
- Keep datasource and pool values externalized. Never commit credentials, tokens, or environment-specific secrets.
- No database migration or schema-management convention exists. Do not enable implicit schema mutation or introduce ad hoc production DDL without an explicit migration decision.
- No stable domain model, API envelope, exception schema, or route-versioning policy exists. Use the feature-based package convention defined in `harness/project/rules/architecture-and-domain.md` without inventing other unstated conventions.
- Changes to routes, request/response shapes, status codes, authentication behavior, entity mappings, or persisted schema require focused tests and explicit compatibility consideration.
- For commit requests, follow `harness/project/rules/git-commit-handoff.md`; the user runs staging and commit commands.

## Project Configuration

See `harness/project/profile.md`

## Navigation

| Content | Path |
| --- | --- |
| Project config | `harness/project/profile.md` |
| Project rules | `harness/project/rules/` |
| Task templates | `harness/core/templates/` |
| Stage gates | `harness/core/gates/` |
| Generic rules | `harness/core/rules/` |
| Automation | `harness/core/automation/` |
| Operations doc templates | `harness/core/operations/` |

## Onboarding a New Project

```bash
git submodule add https://github.com/jxiaow/agent-harness.git harness/core
mkdir -p harness/project/rules
```

Then give the AI a one-liner:

> Read `harness/core/ONBOARD.md` and generate the profile and rules for this repository.

The AI will automatically complete: generating `harness/project/profile.md`, project rules, and `AGENTS.md`.

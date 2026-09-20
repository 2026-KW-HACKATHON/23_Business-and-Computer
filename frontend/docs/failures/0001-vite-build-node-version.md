# 0001. `vite build` crashes on Node 21 (styleText array format)

## Date Observed

2026-09-20

## Failure Type

Cross-environment mismatch / failed completion-gate check.

## Goal

`npm run check` (specifically `npm run build` → `vite build`) should complete so
agents have a working completion gate.

## What Happened Or Was Tried

Running `npm run check` on Node **v21.7.3**. The structure check, ESLint, and
`tsc -b` all pass, then `vite build` crashes inside rolldown:

```
TypeError [ERR_INVALID_ARG_VALUE]: The argument 'format' must be one of: ...
    Received [ 'underline', 'gray' ]
    at styleText (node:util:210:5)
    at styleText$1 (.../node_modules/rolldown/dist/shared/create-bundler-option-*.mjs)
```

## Why It Failed

- Cross-environment mismatch: Vite 8 / rolldown call `util.styleText` with an
  **array** of styles (`['underline','gray']`). Passing an array to
  `util.styleText` is only supported on Node.js >= 22. On Node 21 it throws.
- The failure is in the toolchain/runtime, not in the app source or the harness.

## Current Replacement

No code change made. The fix is to run the build on a supported Node version.
Vite 8 requires Node 20.19+ / 22.12+; use Node 22 LTS (or newer) for `vite build`
and `npm run check`. A local `.nvmrc` / `engines` pin was **not** added here
because Node/build-tool version policy is a separate decision for the
maintainers — see `docs/decisions/` if that policy is later set.

## Detection Or Prevention Check

`npm run check` (the documented completion gate) detects it immediately: the
`vite build` step fails loudly on an unsupported Node version. If a Node version
policy is adopted, add an `engines.node` field to `package.json` (npm warns) or
an `.nvmrc`, and this note names that as the prevention point.

## Agent Guidance

If `npm run check` fails only at the `vite build` step with the `styleText` /
`ERR_INVALID_ARG_VALUE` error, do not "fix" it by editing app code, Vite config,
or the harness. Check `node --version` first; run on Node 22+ and retry. The
structure check, ESLint, and `tsc -b` results are still valid signals.

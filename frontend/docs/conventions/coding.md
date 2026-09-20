# Frontend Coding Conventions

Conventions for the 가꿈 React 19 + TypeScript + Vite app that are **not** fully
enforced by ESLint or `tsc`. Agents should follow these and add an automated
check when a convention is repeatedly missed.

## Stack

- React 19 with the automatic JSX runtime (`"jsx": "react-jsx"`); do not import
  `React` just to use JSX.
- TypeScript in strict bundler mode (`tsconfig.app.json`): `noUnusedLocals`,
  `noUnusedParameters`, and `verbatimModuleSyntax` are on.
- Vite 8 for dev/build. ESLint 10 flat config in `eslint.config.js`.
- npm is the package manager (`package-lock.json`). Do not introduce pnpm/yarn.

## Naming

- Components: `PascalCase`, one component per file, filename matches the export
  (`App.tsx`).
- Hooks: `use` prefix (`useCount`), and only call them at the top level of a
  component or another hook.
- Non-component modules and variables: `camelCase`.
- Assets imported by components live in `src/assets/`; static files served at the
  root live in `public/`.

## Imports & Modules

- Use `import type { ... }` for type-only imports — `verbatimModuleSyntax`
  rejects mixing value and type imports.
- Keep relative imports shallow; do not reach more than two parent levels
  (`../../`). No path aliases are configured — do not add one incidentally.
- Import assets through the bundler (`import logo from './assets/x.svg'`) rather
  than hardcoding `/src/...` URLs.

## React Patterns

- Never fetch data or run side effects directly during render; put them in
  `useEffect` and satisfy the `react-hooks` exhaustive-deps rule.
- Prefer small, focused components; extract shared logic into custom hooks.
- Prefer `const` and immutable updates for state; the ESLint config enforces
  `prefer-const`.

## Error Handling

- Surface user-facing failures in the UI, not `console` alone; keep `console`
  usage out of committed code paths unless intentional (it is lint-noisy).

## Testing

- No test runner is configured yet. When one is added, prefer Vitest +
  React Testing Library (the react profile assumes `vitest --run`), colocate
  `*.test.tsx` next to the unit under test, and wire it into `npm run check`.

## Do Not Touch

- `dist/`, `node_modules/`, `.vite/`, `*.tsbuildinfo` — generated, git-ignored.
- `harness-starter-kit/` — read-only reference clone, git-ignored.

## Agent Notes

When a convention here is repeatedly missed, make it more specific and add an
ESLint rule, a type check, or a `scripts/check_*.py` check where practical.

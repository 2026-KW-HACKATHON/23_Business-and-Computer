# Directory Structure

The target layout for `src` is a **hybrid**: reusable code is organized by
layer, and domain code is grouped by feature. New code should follow this;
existing starter files are not force-moved (see the current-state note below).

## Target layout

```
src/
├─ main.tsx              # entry point, mounts <App>
├─ App.tsx               # root component / routing shell
├─ assets/               # static files imported by components (images, svg)
├─ styles/               # global CSS (index.css, App.css)
├─ components/           # reusable, domain-agnostic UI (Button, Modal, ...)
├─ pages/                # route/screen-level components
├─ features/             # domain features, grouped per feature
│  └─ <feature>/
│     ├─ components/     #   components used only by this feature
│     ├─ hooks/          #   hooks used only by this feature
│     ├─ api/            #   backend calls for this feature
│     ├─ types.ts        #   feature-local types
│     └─ index.ts        #   public entry — other modules import from here only
├─ hooks/                # shared hooks used across features (useXxx)
├─ api/                  # backend API client (base fetch wrapper, shared config)
├─ lib/                  # framework-agnostic pure helpers
└─ types/                # shared, app-wide TypeScript types
```

Create a directory only when it gets its first real file. Do not commit empty
placeholder folders.

## Placement rules

- A route or screen → `pages`. A reusable, domain-agnostic UI piece →
  `components`. Domain-specific code → `features` under its own feature folder.
- Backend calls go through `api` (shared client) or a feature's own `api`
  module. Components and pages must not call `fetch` / the network directly.
- Hooks used by more than one feature → `hooks`. A hook used by one feature →
  that feature's `hooks`.
- Pure, framework-agnostic helpers → `lib`. Do not put React-aware code in
  `lib`.
- Global CSS → `styles`. Component-specific styles are colocated next to the
  component.
- App-wide shared types → `types`. Feature-local types stay in the feature.

## Boundary rules

- A feature is a black box: import a feature only through its `index.ts` public
  entry. Do not import another feature's internal `components`, `hooks`, or
  `api` files directly.
- Dependency direction: `features` and `pages` may use `components`, `hooks`,
  `lib`, `api`, `types`; the reverse is not allowed (shared layers must not
  import from `features` or `pages`).
- Do not import across more than two parent levels (`../../`). If a path gets
  deeper, the file is probably in the wrong layer.
- No path aliases are configured. Do not add one incidentally — if aliases are
  wanted, record the decision in `docs/decisions/` and wire `tsconfig` +
  ESLint together.

## Current state

`src` is currently flat: `main.tsx`, `App.tsx`, `assets`, plus `App.css` /
`index.css` at the `src` root (styles are not yet in a `styles` folder). Apply
the layout above to **new** code. Moving the existing starter files is a
separate, optional refactor and is not required by this rule.

## Enforcement

Today this is documented convention checked in review. To enforce it
mechanically later (no new dependency added now):

- Feature-boundary and import-depth rules: the `no-restricted-paths` rule from
  `eslint-plugin-import`, or `no-restricted-imports`, in `eslint.config.js`.
- Add such a rule when a boundary is repeatedly crossed, and note it in
  `docs/conventions/coding.md`.

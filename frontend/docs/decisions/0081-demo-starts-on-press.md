# 0081. 둘러보기 starts on press and loads the home right after login

## Status

Accepted. On the 둘러보기 role screen the demo login starts when a finger or
the mouse button goes down on a role card, not when it is released. Right
after any demo login (entering or switching role) the home answer is asked
for, so the home screen can use it.

## Context

- Measured on the deployed site after the 31st deploy: 「둘러보기 ›」 →
  first home took about 1.7 s: POST /demo/login about 1.26 s (a new demo
  pair with sample data), then GET /me/home about 0.34 s, one after the
  other.
- A tap's press and release are usually 80–150 ms apart, and the home screen
  only asks for its data after the route has rendered.
- The server side of the demo login (sample data inserts) is handled
  separately.

## Decision

- `RoleCard` takes an optional `onPress`, called on `pointerdown` of the main
  button. `RoleSelectPage` (demo mode) starts `startDemo(role)` there and
  keeps the promise; the click (`onSelect`) waits for that same promise
  instead of starting another login.
  - Pressed on one card but clicked the other: the click waits for the
    first login, then enters the clicked role (same demo pair, only the role
    changes).
  - Pressed but never clicked (the finger slid away): the pair is made and
    stored like a finished login; the next click reuses it.
  - Keyboard (Enter/Space) still starts on click.
- `prefetchDemoHome()` (`src/features/auth/api/demoApi.ts`) sends
  GET /me/home right after the demo login answer is stored. The home answer
  is in the tab list cache (ADR 0077), so the home screen joins the request in
  flight or uses its fresh answer instead of sending its own.

## Rationale

- Both changes only move work earlier; the requests and their answers are
  the same as before.
- The prefetch also helps the home badge 「○○으로 보기 ⇄」, which uses the
  same demo login.

## Alternatives Considered

- **Create the demo pair as soon as the role screen opens**: hides the whole
  wait, but makes a demo pair for every visit even without a click (and
  counts against the hourly limit).
- **A pool of ready demo pairs on the server**: fastest, but a larger backend
  change with its own clean-up rules.

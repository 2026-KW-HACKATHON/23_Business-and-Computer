# 0024. 둘러보기 uses the demo login and shows a strip, not separate routes

## Status

Accepted. Replaces the demo routes in ADR 0006 (/demo/owner, /demo/student).

## Context

The backend now serves `POST /demo/login` (only where `demo-login.enabled`
is on), open without a token:

- Body `{ role: "OWNER" | "STUDENT", demoSessionId? }`.
- Without `demoSessionId` it creates a demo owner and demo student pair with
  sample data and logs in as the requested role. With it, it logs in as that
  role of the same pair. An unknown id is refused as an auth error.
- The answer has no envelope: `{ accessToken, demoSessionId }`. The refresh
  token comes as the HTTP-only cookie, as with the Kakao login, so POST /refresh
  and `apiData` work unchanged.
- New pairs per hour are capped: 429 `DEMO_429`.
- The token carries only `sub`, `role`, and `type`, so the frontend cannot
  tell a demo token from a Kakao one by itself.

ADR 0006 planned /demo/owner and /demo/student so the URL shows the demo.
Demo accounts now use the real API, so every screen would need a second
route, and the URL could still disagree with the account actually logged in.

## Decision

- The routes stay the same for both. 둘러보기 lands on /owner or /student;
  the account decides what the screens show.
- `src/api/tokens.ts` keeps `demoSessionId` next to the access token
  (`saveDemoLogin`, `getDemoSessionId`). `clearTokens` removes it too, so
  logging out or a failed refresh ends the demo. `subscribeSession` lets
  screens re-render when either changes.
- The auth feature (`src/features/auth/lib/demo.ts`):
  - `startDemo(role)` is used by /demo/role. It reuses a stored pair; if the
    server no longer knows it, it starts a new pair.
  - `switchDemoRole(role)` is used by the home badge. If the pair is gone,
    it clears the tokens.
  - Results are `ok`, `limit` (429), `expired` (401/403/404), and `failed`.
- /demo/role (`src/pages/RoleSelectPage.tsx`):
  - Pressing a card shows loading dots (ADR 0059) on its button, locks
    both cards, and sends one
    request even on a double click. On success it goes to `landingPath()`.
  - `limit` shows 「지금은 둘러보기를 더 열 수 없어요. 잠시 후 다시 시도해
    주세요」 under the cards; any other failure shows 「둘러보기를 시작하지
    못했어요…」.
- While a demo session and a token exist (`useIsDemo`):
  - `src/main.tsx` renders the auth feature's `DemoSessionStrip`, which
    draws `DemoStrip` 「둘러보기 중 · 체험용 데모 계정이에요」 (24 px,
    `--color-main`) above every screen.
  - `:root:has(.demo-strip)` sets `--demo-strip-height`, and `SubScreen`,
    `MainTabScreen`, and `DoneScreen` subtract it, so no page scrolls
    because of the strip.
- Home only: `OwnerTabScreen` and `StudentTabScreen` pass
  `useDemoRoleSwitch` to the app bar's existing `DemoRoleBadge`. On success
  it goes to the other role's home. If the pair is gone, it alerts
  「둘러보기 시간이 끝났어요…」 and goes to /login. Any other failure alerts
  「역할을 바꾸지 못했어요…」.
- `CookiePage` clears the demo session before saving a Kakao token, so a
  Kakao login never shows the strip.
- Screens only open for their role: `RoleRouteGuard` (auth feature, wrapped
  around `App` in `src/main.tsx`) checks /owner/…, /explore/… (owner) and
  /student/… against the token's role, and redirects to `landingPath()`
  before the page renders when they differ. It re-checks when the token
  changes, so after a badge switch, 「뒤로」 to the other role's screens
  lands on the current role's home instead of calling APIs with the wrong
  role (the backend answers 403, e.g. 가게 목록 for an owner token).
- Figma: the 「사장님 홈 · 둘러보기 모드」 and 「학생 홈 · 둘러보기 모드」 frames
  show the strip. Notion 「화면 상태 전환표」 and 「페이지 주소 정리」 follow
  this ADR.

## Rationale

- One set of routes avoids keeping two copies of every screen's paths in
  step, and the strip and badge always reflect the stored session, not the
  URL.
- Keeping the demo id in the token store ties its lifetime to the token
  without extra cleanup code.

## Alternatives Considered

- Separate /demo/... routes (ADR 0006): rejected for the cost above, and
  because a URL can claim 둘러보기 while a Kakao token is in use.
- A badge on the home only: rejected, the user wanted the demo visible on
  every screen.

## Agent Guidance

- New screens built on `SubScreen`, `MainTabScreen`, or `DoneScreen` get
  the strip spacing for free. A page with its own full-height root must
  use `calc(100svh - var(--demo-strip-height))` the same way.
- Calling `POST /demo/login` creates accounts on the shared server. Check
  this flow by mocking the response in the browser, and let the user press
  the real button.

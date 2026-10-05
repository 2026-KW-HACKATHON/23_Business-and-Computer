# 0015. Landing by role after login

## Status

Accepted. Replaces the 「/cookie and the intro go to /home」 part of ADR 0009.

## Context

After a Kakao login, `CookiePage` stored the tokens and always went to the
temporary /home, so a first-time user never reached signup and an owner
never reached the owner home. The Notion 「화면 상태 전환표」 says a first
login goes to 회원가입 - 역할 선택 and a signed-up user goes to the home for
their role. The backend has no 「who am I」 API, but the access token carries a
`role` claim: PENDING before signup, OWNER or STUDENT after.

## Decision

- `getUserRole` in `src/features/auth/lib/session.ts` reads the `role`
  claim from the stored access token (no signature check; the backend still
  checks every request).
- `landingPath` picks the first screen: no token → /login, PENDING →
  /signup/role, OWNER → /owner, STUDENT → /home. A token whose role cannot
  be read (or is unknown) is cleared and the user goes to /login, so an owner
  never ends up on the student /home by accident. (Updated in PR #101; before,
  anything else went to /home.)
- `CookiePage` and `IntroPage` go to `landingPath()`. `HomePage` redirects
  owners and pending users the same way. The catch-all route renders
  `src/pages/LandingRedirect.tsx`, which goes straight to `landingPath()`
  instead of passing through /home (PR #101).
- Social-login tokens arrive as `ROLE_ROLE_PENDING` (the prefix is added
  twice on the backend), while signup tokens use `ROLE_OWNER`. Every leading
  `ROLE_` is stripped so both forms work.

## Rationale

- Reading the claim needs no new backend API and no extra request.
- One function keeps the intro, the cookie route, and the fallback in step.

## Alternatives Considered

- A 「me」 API call after login: better long term, but it does not exist yet.

## Agent Guidance

- Students stay on /home until the student home exists; then return its
  path from `landingPath` and drop the student case from `HomePage`.
- Signup does not call the owner or student signup API (POST /auth/owner,
  /auth/student) yet, so the token stays PENDING after the signup screens.
  Wiring signup must save the new tokens it returns.

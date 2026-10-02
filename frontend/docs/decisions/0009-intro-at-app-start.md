# 0009. Intro at app start, no splash

## Status

Accepted

## Context

ADR 0005 planned a splash screen at / that would choose onboarding, login, or
home, with a first-run flag so onboarding showed once. ADR 0007 replaced the
onboarding slides with the 3-second 「로그인 전 히어로」 but kept that plan.
The team has now dropped the splash screen.

## Decision

- / renders the intro (`src/pages/IntroPage.tsx`) on every app start. After
  3 s (with a 0.3 s fade) or on tap it goes to /home if a token is stored,
  otherwise to /login, replacing the history entry.
- The temporary home moves from / to /home. /cookie sends a signed-in user to
  /home, and the catch-all route redirects to /home so an unknown path does
  not replay the intro.
- /onboarding and the first-run flag (src/features/onboarding (removed),
  `localStorage` key `onboardingSeen`) are removed: nothing reads them once the
  intro shows on every start.

## Rationale

- Without a splash, the intro is the app's first screen, so it takes the
  splash's job of choosing the next route.
- The intro is short and skippable by tap, so showing it on every start costs
  little.

## Alternatives Considered

- Keep the intro first-run only: rejected, returning users would land on a
  blank decision point that only the removed splash handled.

## Agent Guidance

- When real role homes exist (/owner, /student), send the intro and /cookie
  there instead of /home.
- Update the Notion 「화면 상태 전환표」: remove 스플래시 and 온보딩 rows and
  describe / as the intro.

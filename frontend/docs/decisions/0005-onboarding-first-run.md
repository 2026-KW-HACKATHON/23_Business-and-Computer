# 0005. Onboarding route and first-run flag

## Status

Accepted

## Context

The Notion 「화면 상태 전환표」 defines onboarding as a three-step intro shown
only on first run. Steps 1 and 2 advance with 「다음으로」; step 3 「시작하기」
and 「SKIP」 on every step go to login and remember that onboarding was seen.
The splash screen at / decides whether to send the user to onboarding, login,
or home, but the splash is not implemented yet.

## Decision

- Add /onboarding rendered by `src/pages/OnboardingPage.tsx`. The step
  (1 / 2 / 3) is component state, not a route, matching the Figma variant set
  「온보딩」.
- 「시작하기」 and 「SKIP」 call `markOnboardingSeen()` and navigate to /login
  with `replace`, so browser back does not return to onboarding.
- The seen flag lives behind `src/features/onboarding` (`hasSeenOnboarding`,
  `markOnboardingSeen`), stored in `localStorage` under `onboardingSeen`.
  Storage errors are swallowed: a failed read counts as first run.

## Rationale

- One route plus a step state keeps browser history clean and follows the
  Notion rule 「상태마다 라우트를 만들지 않는다」.
- Keeping the flag behind a feature entry lets the splash read it later
  without depending on the page.

## Alternatives Considered

- One route per step (/onboarding/1 …): rejected by the Notion rule above.
- Redirecting /onboarding to /login when already seen: deferred to the
  splash screen, which owns first-run routing. The route stays reachable
  directly for demos and QA.

## Agent Guidance

- When the splash is built, read `hasSeenOnboarding()` from
  `src/features/onboarding` to choose between /onboarding and /login.
- Keep slide copy and images in sync with the Figma variant set 「온보딩」.

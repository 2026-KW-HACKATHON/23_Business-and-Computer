# 0007. Login hero, usage cards, and role cards

## Status

Accepted

## Context

After the mid-presentation the Figma page 「1. 공통 (온보딩·로그인)」 changed:

- The three onboarding slides are gone. A 「로그인 전 히어로 (잠깐 보임)」 screen
  takes their place: app icon, slogan, and the five-step service flow
  (제안 또는 의뢰 → 작업 시작 → 초안 → 수정 → 완료) appearing one by one, then an
  automatic move to login after about 3 seconds. Tapping moves on at once.
- The login screen shows the 가꿈 logo, 「가꿈은 이렇게 돌아가요」, and the
  「사용법 카드 넘기기」 carousel (제안 → 공감 → 의뢰, auto-advance every 2 s,
  swipe or tap to advance, three page dots) instead of the app icon hero.
- Both role-select screens use the new 「카드 / 역할 선택」 cards (character,
  role name, one-line description, role-colored button) and the new
  「로고 / 한 줄 소개 (역할 선택)」 tagline.

Figma page 「5. 개선안 (흐름 한눈에)」 proposes a different start screen (flow bar
inside login, one role list). The team chose page 1 for these screens.

The Notion 「화면 상태 전환표」 still lists 온보딩 at /onboarding (last edited
9/27, before the redesign).

## Decision

- Keep the /onboarding route and the first-run flag from ADR 0005. The page
  now renders the hero instead of slides. It leaves after 3 s with a 0.3 s
  fade, or at once on tap, and marks onboarding as seen in both cases.
- Login uses `UsageCardCarousel`. The Kakao, demo, and Notion entries are
  unchanged (ADR 0006).
- /signup/role and /demo/role keep one page with a mode (ADR 0006). Cards use
  `RoleCard`; the button reads 「시작하기 ›」 or 「둘러보기 ›」, and the whole card
  is the tap target. Next routes are unchanged.
- New shared components from the style guide: `RoleCard`,
  `UsageCardCarousel`, `FlowBar` (흐름 막대 / 크게), `TurnNotice` (알림 / 내 차례).
  `TextField` gains `trailing`, `TextButton` gains `showFilter`.
- Images only the old screens used are removed: `onboarding1`–`3`,
  `characterBadgeOwner`/`Student`, `tagline`, `iconChevronRight20`.
- `StepIndicator` keeps the role colors. The style guide now shows `#3182f6`,
  which is not bound to any Figma variable; the team treated it as a stray
  value.

## Rationale

- Reusing /onboarding and its flag keeps the splash contract from ADR 0005
  intact; only the screen content changed.
- Making the whole role card one button matches the Figma note 「카드 어디를
  눌러도 같은 곳으로 간다」 and avoids nested interactive elements.

## Alternatives Considered

- A new /intro route for the hero: rejected, it would add a route while the
  flag and splash logic stay the same.
- Following page 5 for the start screens: not chosen by the team.

## Agent Guidance

- Update the Notion 「화면 상태 전환표」 row 「온보딩」 to describe the hero
  (3 s auto, tap to skip) when the team edits Notion.
- Owner and student screens follow Figma pages 2·3, not page 5.
- Use `FlowBar` and `TurnNotice` for the flow bar and 「내 차례」 notices on
  those screens instead of drawing them per page.

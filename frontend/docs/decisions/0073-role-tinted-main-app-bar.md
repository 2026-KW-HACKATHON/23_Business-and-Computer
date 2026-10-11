# 0073. Main tab app bar is tinted by role

## Status

Accepted. On the main tab screens (홈 · 탐색 · 채팅) the top app bar is light
owner yellow for owners and light student magenta for students, instead of
the shared gray.

## Context

- In 둘러보기 the same browser switches between the owner and the student
  side with the role badge. Both sides had the same gray app bar, so the
  only hint of the side was the small logo character.
- The student 탐색 tabs (제안·의뢰 / 가게) sit right under the app bar on the
  same gray and must keep reading as one header.

## Decision

- Tokens `--color-owner-bar` (#fff6d9) and `--color-student-bar` (#fceff4)
  in `src/styles/tokens.css`.
- `MainAppBar` sets `--main-app-bar-bg` per role (`main-app-bar--owner` /
  `--student`) and uses it for the bar, the safe-area top, and the ring
  around the unread dot on the bell.
- The student 탐색 tab row uses `--color-student-bar` so it continues the
  bar.
- On the tinted bar the 둘러보기 role badge (「학생으로 보기 ⇄」) has a white
  background so it does not blend into the yellow.
- Sub screens (← app bars) keep their current look.

## Rationale

- Light tints separate the two sides at a glance without competing with the
  yellow and magenta primary buttons below.
- One variable per bar keeps the dot ring and the tab row in step with the
  bar color.

## Alternatives Considered

- **Coloring the bottom tab bar instead**: the user asked for the top bar,
  which is also what changes first when the role switches.
- **Full owner yellow / student magenta bars**: too strong next to the
  「맡길 일 찾기」 · 「나에게 맞는 일 찾기」 banners and primary buttons.

# 0061. Press feedback for text-style and chip-style controls

## Status

Accepted. Builds on ADR 0046. Text links and text buttons fade instead of
shading, card titles shade their card, and small cells, arrows, tabs, and ✕
buttons shade in a rounded shape. Replaces the 6px rounded shade of the
`SectionHeader` and `ResendButton` actions in ADR 0046.

## Context

- ADR 0046 shades every `button`, `a[href]`, and `[role="button"]` with an
  inset shadow over its background.
- On a control with no padding and square corners, that shade is a gray
  rectangle tight around the text: the explore card titles, the 요약 카드
  cells, 재전송 · 수정 · 신고 text buttons, the carousel arrows, and the
  explore tabs.

## Decision

- **Text links and text buttons**: the shared `.press-text` class
  (`src/styles/interaction.css`) turns the shade off and lowers the opacity
  instead, to 70% on hover and 50% while pressed; disabled ones do not
  react. Used by the `SectionHeader` action, `ResendButton`, the chat
  다시 보내기, 신고 on the owner work check, past submission, and student
  work screens, the terms 보기 on both signup info screens, the owner signup
  verify 정보 수정, the student signup verify 변경, and the portfolio link
  「열기 ›」 on both student profile screens.
- **Card titles that open the card** (`src/features/student/components/ExploreCards.css`,
  the student explore request and proposal cards): the title has no shade;
  the card shades with `.student-card:has(.student-card__title:hover)` and
  `:active`, 4% and 8% like ADR 0046. The title keeps its focus ring.
- **Cells, arrows, tabs, and ✕** get padding and round corners; a negative
  margin of the same size keeps the layout unchanged:
  - `SummaryCard` cells outside the tab mode (내 정보): 6px above and below,
    14px corners, like the tab mode.
  - The 확인할 일 carousel arrows (owner and student): pill corners.
  - `ExploreTabs`: 32px-high pills with 10px side padding and 4px margin above
    and below; the current tab's underline stays at the bottom of the 40px
    strip under the text.
  - The 다른 학생들의 제안 row opener on the student home: 8px padding, 12px
    corners.
  - ✕ on picked tasks (의뢰 등록 1/3, 제안 보내기 2/4), on photo and file
    rows (의뢰 등록 2/3, 제안 보내기 3/4, `FilePicker`, 수정 요청 photos):
    4px padding, pill corners.
- **Rows in a bordered box** (`MenuList`, 결제 내역, 정산 내역, 포트폴리오): the side
  padding sits in each row instead of the box, so the shade fills the box from
  border to border; the box clips to its round corners, the first and last rows
  round the same way, and the divider is a 1px line from where the text starts.
- **Open list rows** (`TaskRow` on the homes, the explore sort options): the row
  box, dividers, and text stay as they are; a `::before` box 12px wider than
  the text on each side, with 12px corners, carries the shade and the focus
  ring.
- **Tab bar** (`TabBar`): the tabs have no shade, so the only background that
  moves is the sliding selection of ADR 0055; before, the shade painted the
  pressed tab, and the hover shade on the newly drawn tab bar, at once while
  the selection was still sliding. Under the mouse a tab that is not current
  turns its text and icon `--text-title`; pressed, its icon shrinks to 90%
  (replacing the 94% tab shrink of ADR 0046). The new tab's text darkens from
  gray as the selection arrives (0.2 s after a 0.12 s delay), like the icon
  pop.
- Without a mouse only the press reacts, as in ADR 0046; `.press-text` and
  the card hover sit in `@media (hover: hover)`.
- `prefers-reduced-motion` turns the `.press-text` and card transitions off.

## Rationale

- Fading keeps a text control looking like text; a gray box tight around a
  word reads as a rendering glitch.
- A card title stands for the whole card, so the card is what reacts.
- Padding plus an equal negative margin gives the shade room without moving
  anything around it.

## Alternatives Considered

- Making the whole explore card one button: the card holds other buttons
  (지원하기, the TextButton, 공감), and buttons cannot nest.
- Padding on text buttons as for `TextButton`: these sit in tight rows
  (input trailing, chat bubble footer, section header) where a box crowds
  the line.

## Agent Guidance

- A new text link or text button without padding gets `press-text`.
- A new small control that keeps the shade gets padding and round corners,
  with a negative margin when its size must not change.
- Large buttons, cards, list rows, `TextButton`, `StarRating`, `KindTabs`,
  and `TabBar` keep ADR 0046 as is.

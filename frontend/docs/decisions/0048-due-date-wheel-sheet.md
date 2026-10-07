# 0048. Due dates are picked on a wheel in a bottom sheet

## Status

Accepted. 의뢰 등록 2/3 「초안 마감」 · 「최종 마감」 open a bottom sheet with a
year · month · day wheel instead of the browser's date picker, as in the Figma
「날짜 바퀴」 component and the 「의뢰 등록 2/3 - 초안 · 최종 마감일 고르기
(팝업)」 screens.

## Context

- The due boxes used a hidden native date input (`showPicker`), so every
  browser and phone showed a different calendar, unlike the design.
- The user approved the wheel sheet in Figma and chose 올해 and 내년 as the
  range.

## Decision

- **`DateWheel`** (`src/components/DateWheel`): the picked day on top
  (「2026년」 / 「9월 27일 (일)」), three columns (연 · 월 · 일) with a gray bar on
  the middle row, and 「위아래로 밀거나 숫자를 눌러 골라 주세요」.
  - Each column scrolls with scroll snapping (44px rows, five visible); the row
    that stops in the middle is picked 120ms after scrolling ends. Tapping a
    number scrolls to it and picks it; ↑ ↓ change a focused column.
  - Rows shrink and fade with distance from the middle (20px bold black, 17px
    gray, 15px light gray).
  - Days before `min` and after `max` are not listed. When the year or month
    changes, the day is clamped into range (31 → the month's last day).
  - Columns are `listbox`es with `option`s and `aria-activedescendant`.
- **Due boxes** (`DueBox` in `src/components/FormFields/FormFields.tsx`): a
  button that opens `BottomSheet` titled 「초안 마감일」 / 「최종 마감일」:
  - 초안: 「학생이 초안을 보내는 날이에요」, from today;
  - 최종: 「초안 마감(9월 27일)과 같거나 뒤로 골라 주세요」, from the draft due
    date (or today);
  - both end on December 31 of next year;
  - 「이 날짜로 정하기」 saves; the backdrop or Esc closes without saving.
- Values stay `YYYY-MM-DD`; moving 초안 past 최종 still clears 최종. The
  request API is unchanged.

## Rationale

- One look on every device, matching the approved design.
- Leaving out unpickable days removes the error state the native picker
  needed.

## Alternatives Considered

- A month calendar grid: rejected, the user chose the wheel design.
- An X button on the sheet: not added; like every other sheet it closes on
  the backdrop.

## Agent Guidance

- Use `DateWheel` in a `BottomSheet` for any new date field instead of
  `<input type="date">`.

# 0043. Short screens shrink the spacing of the full-height pages

## Status

Accepted. The intro, 로그인, 역할 선택 (가입 and 둘러보기), and 가입 pages
tighten their spacing on short screens, so the whole page fits without a
scroll. Screens taller than 700px keep the Figma layout.

## Context

- On an iPhone in Safari, the bottom toolbar leaves about 550–660px of
  height. At 375×548 the intro (721px), 로그인 (650px), and 역할 선택
  (639px) pages were taller than the screen, so their last line (the intro
  note, the Notion link, the role cards) sat below the toolbar.
- `SubScreen`, `MainTabScreen`, and `DoneScreen` already fit: they are fixed
  to the screen height and scroll inside, with the footer always visible.
  The pages above are their own `min-height: 100svh` roots with Figma's
  fixed spacing.

## Decision

- Two height steps in each page's CSS, after the Figma styles:
  - `@media (max-height: 700px)`: smaller gaps and top/bottom padding.
  - `@media (max-height: 600px)`: also smaller pictures and text — the
    intro app icon (96 → 64px), title (22px), and step rows (28px); the
    로그인 logo (160 → 128px, `className="login__logo"`).
- The intro step connector stays as long as the step gap at each step.
- The owner 가입 form (649px at 548) still scrolls; it is a long form and
  its 다음 button stays reachable.

## Rationale

- Height queries change only short screens, so phones taller than 700px
  keep the Figma look.
- Spacing shrinks first; pictures and text shrink only below 600px, where
  spacing alone is not enough.

## Alternatives Considered

- Fixing these pages to the screen height with an inner scroll, like
  `SubScreen`: rejected, the last line would still sit below the toolbar
  until the user scrolls.
- `vh`-based spacing: rejected, it changes the Figma layout on every screen
  height.

## Agent Guidance

- A new full-height page (`min-height: 100svh`) must fit at 375×548. Add
  its short-screen spacing in the same two `max-height` steps.
- Check at 375×548, 375×664, and 375×844; above 700px the computed values
  must match Figma.

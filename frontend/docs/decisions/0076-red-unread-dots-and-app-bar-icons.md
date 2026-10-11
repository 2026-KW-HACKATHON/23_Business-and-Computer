# 0076. Red unread dots and app bar icon sizing

## Status

Accepted. The unread dots on the app bar 알림 bell and on the 「채팅」 tab are
one red for both roles, the 내 정보 icon matches the bell in size, and the
two app bar icons have 40px touch targets with a press reaction.

## Context

- The dots used the role color. The owner yellow (#fed34d) on the light
  yellow app bar (#fff6d9) or the white tab bar was close to 1.3:1, so an
  owner could not see that something was unread.
- The bell dot sat next to the bell's top knob and read as part of it.
- The 내 정보 circle filled almost the whole 32px box while the bell body is
  narrower, so the profile icon looked bigger and heavier.
- The icon buttons were 32px, small for a thumb, and the shared press shade
  (ADR 0046) on such a small circle was hard to notice.

## Decision

- Token `--color-unread` (#ff3040) in `src/styles/tokens.css`, used by the
  app bar bell dot (`MainAppBar`) and the 「채팅」 tab dot (`TabBar`).
  `--color-danger` stays for errors.
- Both dots keep their shape: 10px with a 2px ring in the color behind them
  (the app bar tint or white), 6px of red inside.
- The bell dot sits on the bell's right shoulder, clear of the knob
  (`top: 7px; right: 6px` inside the 40px button).
- `my.svg`: the circle is 23px across (radius 11.5) instead of about 26px,
  with the head and shoulders scaled to match. Stroke width stays 2.5 for
  both icons, which suits the bold logo and titles.
- `MainAppBar` icon buttons are 40px with the 32px icon centered. The gap is
  6px and the row reaches 4px past the padding, so the icons stay 14px apart
  and 16px from the right edge as before.
- Pressed, an icon button shrinks to 90% with its dot, like the tab bar
  icons (ADR 0061), on top of the shared darker round shade (ADR 0046).
  `prefers-reduced-motion` keeps only the shade.

## Rationale

- A red unread dot is the convention of common apps (Instagram, iOS), so
  the meaning is clear without a legend.
- #ff3040 stays clear of the student magenta, which a pinkish red such as
  `--color-danger` resembles. It is above 3:1 on the yellow, pink, and white
  backgrounds.
- A larger dot (8px of red) covered too much of the bell's shoulder.

## Alternatives Considered

- **Keeping the role color for students and a darker color only for
  owners**: two dot colors for one meaning, and the owner dot would still
  differ from the chat tab of the same screen.
- **#ff3b30 (iOS red)**: leans orange and blends a little with the owner
  yellow.

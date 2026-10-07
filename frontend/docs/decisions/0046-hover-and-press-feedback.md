# 0046. Hover and press feedback

## Status

Accepted. Everything the user can press reacts: a little darker under the
mouse, darker still while pressed. Role buttons stay in their role colors.

## Context

- Phones showed the browser's gray tap flash, and the desktop showed nothing
  on hover or press, except the role buttons that sink when pressed.
- The user asked for hover and press feedback across the app, with colors left
  to judgment, but owner and student main colors must stay with their role.

## Decision

- **Default** (`src/styles/interaction.css`, imported in `src/main.tsx`): every
  `button`, `a[href]`, and `[role="button"]` that is not disabled gets an
  inset shadow over its background — 4% black on hover, 8% while pressed — so
  text and icons stay sharp. Hover applies only where a mouse can hover
  (`@media (hover: hover)`). The browser tap flash is turned off. Every
  selector sits in `:where`, so component CSS always wins.
- **Role buttons** (`Button`, `ChatButton`, `Fab`): on hover the background
  mixes 14% of the role's own shadow color into the role color (owner yellow
  with `--color-owner-shadow`, student burgundy with
  `--color-student-shadow`); `Button` and `ChatButton` still sink 4px when
  pressed, and `Fab` shrinks to 94%.
- **Shapes**: the app bar icons darken as circles, the back arrow and
  `TextButton` in rounded boxes (padding added and offset by negative margin
  so nothing moves), `SectionHeader` and `ResendButton` actions with a 6px
  radius.
- **Small press motion**: chips (96%), tabs (`TabBar` 94%, `KindTabs` 97%),
  the 둘러보기 badge (96%), the 공감 button (94%), and the 새 의뢰 · 새 제안
  field cards (98%).
- **Own reactions instead of the shade**: `FieldFilter` darkens and shrinks its
  icon tile, `StarRating` grows its star on hover and shrinks it when pressed,
  and `ReferencePhotos` fades the photo on hover and shrinks it inside the
  tile when pressed.
- **No shade**: the intro screen and the 사용법 카드 viewport, which are
  screen-sized buttons.
- `prefers-reduced-motion` turns the transitions off.

## Rationale

- One low-priority default covers every pressable element, including page
  buttons without their own style, and needs no change in the pages.
- An inset shadow tints any background (white, gray, colored, or none) and
  leaves text and icons untouched, unlike `filter: brightness`.
- Mixing toward each role's own shadow color keeps owner screens yellow and
  student screens burgundy.

## Alternatives Considered

- `filter: brightness()` on press: rejected, it also darkens photos and icons
  and creates stacking contexts.
- A press scale on every element: rejected, full-width rows and big cards
  look shaky; scale is kept for small controls.

## Agent Guidance

- A new pressable element gets the default shade for free. If it has its own
  `box-shadow`, the default does not show; give it its own hover and press
  style.
- A role-colored control darkens toward its own role shadow color; never mix
  owner and student colors.
- A screen-sized or photo-covered button sets `box-shadow: none` on
  `:hover`/`:active` and uses its own reaction.

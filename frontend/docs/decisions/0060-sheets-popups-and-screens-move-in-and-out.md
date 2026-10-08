# 0060. Sheets, popups, and pushed screens move in and out

## Status

Accepted. Bottom sheets rise from the bottom, slide down when they close, and
close when dragged down. Center popups, the photo viewer, and the first guides
fade in and out. Checks, the heart, and opened rows appear with a short
motion, and screens opened forward slide in from the right.

## Context

- The user asked for the bottom sheets to come up from the bottom and go down
  when they close. Every sheet (`BottomSheet`) and center popup (`Dialog`)
  appeared and vanished in one frame. The photo viewer (`PhotoViewer`) and
  the first guides (`GuideOverlay`) faded in but vanished at once, because
  their parents remove them on close.
- A search for other missing feedback found:
  - the check in `Checkbox`, the check circle in `Chip` and in the store
    chips (가게 고르기), and the check in the store radio appear at once;
  - `Chip`, `EmpathyCount`, and `StarRating` set their own `transition`,
    which replaces the shared one (ADR 0046), so their colors jump;
  - liking a proposal swaps the heart, and picking a 후기 star fills it, with
    no feedback;
  - 「문제가 있나요?」 on the chat work card and 「끝난 일」 펼치기 on the homes
    show the new items at once;
  - the KakaoPay hand-off screen covers the app at once;
  - Notion's 화면 상태 전환표 says screens other than the tabs slide in from
    the right, but no screen did.
- References: Material 3 and iOS bottom sheets have a drag handle and close
  when swiped down; Material motion makes exits shorter than entrances; its
  shared-axis transition moves the incoming screen about 30 px while it
  fades in.
- Figma: on 「0. 스타일 가이드」 the descriptions of 바텀시트 (node 1908-167),
  체크박스 (1926-170), 공감 수 (835-107), 뱃지 / 역량 칩 (964-222), and
  뱃지 / 업종 칩 (1254-149) state the motion.

## Decision

- **Staying while leaving** (`usePresence` in `src/hooks/usePresence.ts`):
  when `open` turns false, the overlay stays drawn with an `--exit` class for
  its exit time, then unmounts. `Frozen` (`src/components/Frozen/Frozen.tsx`)
  keeps the last content during the exit, so a parent that clears the content
  on close does not empty the sheet as it leaves.
- **Bottom sheet** (`BottomSheet`):
  - enter: rises from its own height over 0.32 s
    (`cubic-bezier(0.32, 0.72, 0, 1)`) while the dim fades in to 40%;
  - exit: slides down over 0.24 s (`cubic-bezier(0.4, 0, 1, 1)`) while the
    dim clears;
  - drag: the handle, header, and title (`.bottom-sheet__grab`, with
    `touch-action: none`) follow a press that moves down more than 6 px, and
    the dim lightens with the distance. Releasing past a quarter of the
    sheet's height, or after a fling faster than 0.6 px/ms, closes it from
    where it is; otherwise it springs back. Moves are read on `window`, so a
    fast fling past the handle still counts, and the click from releasing
    outside the sheet does not close it twice. A press that moves less than
    6 px stays a tap on a header button.
- **Center popup** (`Dialog`): grows from 94% with a fade over 0.22 s and
  shrinks to 96% while fading out over 0.16 s.
- **Photo viewer and first guides**: fade in (the viewer over 0.2 s) and fade
  out (0.16 s, the guide 0.2 s) before telling the parent to close. A second
  close during the fade is ignored.
- **Small feedback**:
  - `Checkbox`: the box fills over 0.15 s and the check grows from 50% with
    a slight overshoot;
  - `Chip`, the store chips, and the store radio: the check grows in from 0
    over 0.2 s; `Chip` also fades its background, border, and text color;
  - `EmpathyCount`: liking pops the heart to 140% and back over 0.4 s; taking
    the like back does not;
  - `StarRating` (후기 작성): a star that turns on fades to yellow and pops
    to 125% and back over 0.3 s;
  - the chat work card's 「문제가 있나요?」 items slide 8 px out of the arrow
    and fade in over 0.2 s;
  - 「끝난 일」 펼치기: the rows after the first come down 6 px and fade in
    over 0.24 s;
  - the KakaoPay hand-off (`PaymentProgress`) fades in over 0.2 s.
- **Pushed screens** (`usePushed` in `src/hooks/usePushed.ts`,
  `.screen-pushed` in `src/styles/motion.css`): when the router's navigation
  type is PUSH, the frame gets `screen-pushed`, and its children (app bar,
  body, footer) slide 32 px in from the right and fade in over 0.3 s
  (`cubic-bezier(0.2, 0, 0, 1)`). The frame clips what overflows
  sideways. `SubScreen`, the six signup steps, and the role select screens
  use it. Going back (POP), replacing (완료 screens and redirects), and
  reloading show the screen at once. Tab changes still have no screen motion
  (ADR 0055).
- `prefers-reduced-motion` turns all of these off, and overlays close at once.

## Rationale

- A sheet that leaves the way it came shows where it went, and dragging it
  down is how sheets close on phones.
- Keeping the overlay drawn inside the component needs no change in the 17
  files that open sheets and popups.
- The frame clips what overflows, so sliding its content in leaves no
  horizontal scroll on phones. The old screen is already gone when the new
  one mounts, so a short slide with a fade reads as going forward without a
  blank gap.
- One hook and one class keep the rule in one place for frames that are not
  `SubScreen`.

## Alternatives Considered

- A motion library (Framer Motion and the like): a new dependency for a few
  transitions that CSS already does.
- Capturing the pointer on press: a tap on a header button would then land
  on the drag area instead of the button.
- Sliding whole screens 100% as iOS does: the old screen would have to stay
  on screen during the move, which the router does not keep.

## Agent Guidance

- A new overlay opened with an `open` prop uses `usePresence` and `Frozen`.
  One that its parent removes on close fades itself out first and calls
  `onClose` after the fade.
- Keep each exit time in code (`EXIT_MS`) equal to its CSS transition.
- A component that sets its own `transition` also lists `background-color`
  and the colors that change on selection, or it loses the shared fade.
- Screens built on `SubScreen` get the slide. A new non-tab screen with its
  own frame adds `screen-pushed` to the frame when `usePushed()` is true; do
  not add another enter animation on top of it.

# 0059. Loading states follow what the screen waits for

## Status

Accepted. A whole screen waiting shows the character loader, a list shows
gray skeletons, a single number shows three dots, and a busy button shows
three dots in place of its label. Nothing shows for loads under 0.3 seconds.

## Context

- Loading looked the same everywhere: a gray line 「…불러오는 중이에요」
  (`LoadNotice`), 「-건」 in the summary numbers, and buttons whose label
  changed to 「보내는 중...」. Login and the KakaoPay hand-off had their own
  text or ring spinner.
- The team made 「골목인턴 로딩 v7」: the owner and student characters hopping
  over three gray dots (SVG, 120×87), with a still frame for reduced motion.
  The dots breathe in turn every 2.4 s (opacity 24–82%, size 80–100%).
- References:
  - Carbon's loading pattern: skeletons for the first load of lists and
    cards, inline loading for one element, full-screen loading only when
    the whole screen waits.
  - Semrush Intergalactic: a skeleton after 600 ms, a small spin for a
    single element.
  - 토스 TDS Button `loading`: three dots move in turn, the width stays,
    `aria-busy` is set.
  - Nielsen's limits: under 1 s needs no indicator. Adobe advises no
    animation for waits under a second, to avoid a flash.
- Figma: 「로딩 (10.8 추가)」 (node 3600-239) on 「0. 스타일 가이드」 holds the
  loader, dots, skeleton, and busy button components; 「카카오페이 이동 중
  (로딩 · 공통)」 (node 1806-2922) shows the owner character.

## Decision

- **Delay**: every loading visual appears after 0.3 s (`useDelayedShow`,
  `LOADING_DELAY_MS` in `src/hooks/useDelayedShow.ts`).
- **Whole screen** (`Loader`, `src/components/Loader/Loader.tsx`): the
  character loader with the message under it, centered in the body. The
  character follows the screen's role (`<html data-role>`), yellow owner or
  white student; reduced motion shows the still frame. `LoadNotice
  layout="page"` uses it on the 39 screens that wait for one thing (details,
  results, payments, reviews, history). The login callback (/cookie) shows it
  over the whole app (`overlay`), and the KakaoPay hand-off uses the owner
  character (`LoaderArt`) in place of the ring.
- **Lists** (`SkeletonList`): gray frames shaped like the content. `rows`
  (icon and two lines) for the home sections, chats, notifications, and store
  lists; `cards` for 내 활동, 탐색, and applicants; `block` for one box (내 정보
  head, 맡은 학생, the latest submission). `LoadNotice` defaults to `rows`.
- **Small values** (`LoadingDots`): three dots in the place of a number
  (`SummaryCard` takes `count: null` while loading; 내 작업물 stats) and at the
  end of a list loading more (`layout="more"`).
- **Buttons**: `Button loading` keeps the role color and width, shows the
  dots in the label color, sets `aria-busy`, and cannot be pressed. A screen
  reader reads `loadingLabel` (예: 보내는 중). `RoleCard loading` does the same
  for 둘러보기.
- Errors keep the one-line message with 「다시 시도」.

## Rationale

- Matching the indicator to what waits keeps the rest of the screen usable
  and shows where content will appear, as the references do.
- One delay for all of them avoids a flash on fast answers.
- Dots in the button keep the layout still while sending, and the label
  returns as it was on failure.

## Alternatives Considered

- One full-screen overlay for every load: blocks screens that are mostly
  ready and flashes on each navigation.
- The character on every list: several characters would run at once on the
  home.
- Keeping 「…하는 중...」 labels: the button width jumps and the label reads
  as text, not progress.

## Agent Guidance

- New screens pick the `LoadNotice` layout by what waits: one thing →
  `page`, a list → `rows` or `cards`, one box → `block`, loading more →
  `more`.
- Buttons that send use `loading` and `loadingLabel`; do not write
  「…중...」 labels.
- The SVGs in `src/assets/loading` come from 「골목인턴 로딩 v7」; the dots are
  CSS (`LoadingDots`) with the same timing and the `--loading-dot-color`
  token.

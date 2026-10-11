# 0070. Owners post, edit, and resolve one store concern

## Status

Accepted. The owner home shows a store-concern card right below 「확인할 일」,
/owner/me/concern writes or edits the concern, and 내 정보 shows it with
「고치기」 and 「해결됐어요」. The home card's data now comes with
`GET /me/home` (ADR 0080).

## Context

- A store concern is reference information for students writing proposals
  (ADR 0069). It is not linked to proposals. One open concern per store; it
  has no deadline and the owner takes it down.
- The backend (branch 340) serves `GET`, `PUT`, and `DELETE
  /owners/me/concern`. `GET` answers without `data` when there is no open
  concern. `PUT` takes `{title (≤60), description (≤500, null clears),
  specialtyCategoryId (null clears)}`.
- The user asked for the card below 「확인할 일」, reading 「현재 올려둔 고민」
  once a concern is posted.

## Decision

- `useOwnerConcern` loads the concern for the home, 내 정보, and the write
  screen. 401 goes to /login; any other failure is `error`.
- Home: `OwnerConcernCard` in its own section after 「확인할 일」 (also on a
  first-visit home). While loading or on error the section is hidden, so the
  rest of the home is unaffected.
  - No concern: an "empty slot" card — white, dashed owner-yellow border, the
    thinking owner illustration (`concernOwner`, 44px, the same one as the posted bubble), 「우리 가게 고민을 남겨 보세요」, 「학생들이
    보고 해결 방법을 먼저 제안해 줘요」, and 「고민 올리기 ›」. The whole card
    opens /owner/me/concern.
  - A concern: the section title 「현재 올려둔 고민」 and a speech bubble
    instead of a card: the thinking owner illustration (`concernOwner`,
    56px) at the bottom left, a white bordered bubble with a tail toward
    the owner's head, a first row with a yellow 「“」 on the left and 「펼치기 ›」
    (bold #a87600, like 「고치기 ›」) on the right, the line (16px bold),
    and 「— 우리 가게 고민 · 분야 · ○월 ○일」 (`createdAt`; no field, no
    middle part). The text button 「고치기 ›」 (bold, #a87600 like 「고민
    올리기 ›」) sits under the bubble on the right. 「펼치기」 opens a gray 「자세한 설명」 box inside the bubble, swaps the
    illustration to the explaining owner (`concernOwnerExplain`; the two
    images are stacked, the thinking one shrinks and fades while the
    explaining one pops up from 85%, 0.32s; none with reduced motion), and
    becomes 「접기」; a concern without a description has no toggle.
- /owner/me/concern (`OwnerConcernPage`): 「한 줄 고민」 (required),
  「자세한 설명 (선택)」, 「분야 (선택)」 chips from `GET /specialties` (one,
  tap again to clear). The button reads 「올리기」 for a new concern and
  「저장하기」 for an edit; on success the screen goes back.
- 내 정보: 「우리 가게 고민」 between the summary card and 「내 활동」. With a
  concern: the concern box, 「고치기」, and 「해결됐어요」, which asks once
  (「고민이 해결됐나요?」, buttons side by side: 「아직이에요」 left, 「해결됐어요」
  right) and then reloads into the empty state. Without one:
  「올려 둔 고민이 없어요 …」 and 「고민 올리기」.
- A 404 on resolve counts as resolved (already taken down elsewhere). A 409 on
  save asks the owner to reopen the screen.

## Rationale

- Keeping the concern on its own endpoint avoids touching `GET /me/home`
  (ADR 0064) and its server tests.
- Hiding the card on failure keeps the home usable before the backend is
  deployed.
- 「해결됐어요」 lives in 내 정보 as the user asked; the home card only opens
  the editor, so a stray tap cannot take a concern down.
- The 「맡길 일 찾기」 banner (gradient promo) can sit on the same home; a
  dashed white "empty slot" keeps the concern from reading as a second promo.
- The 확인할 일 cards right above are white with a yellow border and a full
  button. With the same shape the concern read as one more task, so the
  posted concern is the owner's own words in a speech bubble, with a text
  button instead.
- The detail box header is a small gray 「자세한 설명」 label so it is not read
  as the concern itself.

## Alternatives Considered

- Showing the concern inside the 확인할 일 carousel: concerns are not tasks
  and would be counted as one.
- A resolve button on the home card: one tap would remove the concern from
  every student's list without a second look.
- The same gradient banner as 「맡길 일 찾기」 for the empty state: shown on
  one screen, the two banners looked like the same promo twice.

## Agent Guidance

- Until the backend branch is deployed, `GET /owners/me/concern` fails, so
  the home card is hidden and 내 정보 shows the load error; this is expected.
- Notion and Figma are updated after the whole feature is done.

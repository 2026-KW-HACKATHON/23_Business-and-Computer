# 0070. Owners post, edit, and resolve one store concern

## Status

Accepted. The owner home shows a store-concern card right below 「확인할 일」,
/owner/me/concern writes or edits the concern, and 내 정보 shows it with
「고치기」 and 「해결됐어요」.

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
    owner character (`sorryOwner`), 「우리 가게 고민을 남겨 보세요」, 「학생들이
    보고 해결 방법을 먼저 제안해 줘요」, and 「고민 올리기 ›」. The whole card
    opens /owner/me/concern.
  - A concern: the section title 「현재 올려둔 고민」 and a card shaped like the
    확인할 일 card: the line with 「펼치기 ›」 on its right, the field badge,
    「○월 ○일에 올렸어요」 (`createdAt`), and a secondary 「고치기」. 「펼치기」
    opens a gray 「자세한 설명」 box under the heading and becomes 「접기」; a
    concern without a description has no toggle.
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
- The gray box header is a small gray 「자세한 설명」 label so it is not read as
  the concern itself.

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

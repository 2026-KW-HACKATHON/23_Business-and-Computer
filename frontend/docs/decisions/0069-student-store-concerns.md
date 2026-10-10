# 0069. Students see store concerns on the store tab and while writing a proposal

## Status

Accepted. The student explore 「가게」 tab and the proposal store picker (1/4)
put stores with a concern on top and show the concern line or 「고민 없음」.
The proposal content step (3/4) shows the chosen store's concern as
「사장님 고민」 above the form.

## Context

- An owner can now post one open 「가게 고민」 for the store: a one-line
  concern, an optional description, and an optional field (specialty
  category). The backend keeps it in `store_concerns` (V49) and serves it on
  `GET /owners/me/concern` (backend branch 340).
- A concern is reference information for students writing a proposal. It is
  not linked to proposals, and accepting a proposal does not close it. The
  owner takes it down with 「해결됐어요」.
- `GET /explore/stores` items now carry `concern`
  (`{concernId, title, description, specialtyCategory{id,name}, createdAt,
  updatedAt}` or null). The server order stays registration order; the page
  already loads every page and filters on the client (ADR 0020).

## Decision

- `fetchAllExploreStores` maps `concern` to
  `ExploreStore.concern = {title, description, category, updatedAt}`.
- `sortStoresByConcern` puts stores with a concern first, newest
  `updatedAt` first, then the rest in the order received. Both store lists
  (`StudentStoresPage`, `StudentProposalStorePage`) use it.
- `StoreConcernLine` sits under each store: a purple 「고민」 tag and the
  one-line concern (ellipsis), or a gray 「고민 없음」. The field is not shown
  in the list, so the store's business-category badge stays the only badge in
  the row.
- `StoreConcernCard` sits between the intro and 「제안 제목」 on 3/4 when the
  router state's store has a concern: 「사장님 고민」, the field badge, the
  line, and the description.
- The store tab description reads 「사장님 고민이 있는 가게부터 보여 드려요.」.

## Rationale

- Sorting on the client keeps the store cursor and server tests unchanged;
  the client already holds the whole list.
- The concern travels inside the store object in router state, so 3/4 needs
  no extra request.
- 「고민 없음」 keeps every row the same height, so the list does not jump.

## Alternatives Considered

- A separate 「고민」 tab listing concerns: the user chose to keep the 「가게」
  tab and lift stores with a concern.
- Server-side sorting by concern: it would change the store cursor format for
  no gain while the client loads every page.
- Showing the concern card on 2/4: 3/4 is where the student writes
  「손님 눈으로 본 문제」, which the concern feeds.

## Agent Guidance

- Until the backend branch is deployed, `concern` is absent and every store
  shows 「고민 없음」; this is expected.
- Owner screens (home card under 「확인할 일」, write screen, edit ·
  「해결됐어요」 from 내 정보) come in the next unit. Notion and Figma are
  updated after the whole feature is done.

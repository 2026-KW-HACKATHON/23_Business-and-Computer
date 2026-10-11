# 0071. Student home shows newly posted store concerns

## Status

Accepted. The student home shows 「새로 올라온 가게 고민」 right below
확인할 일: up to three stores with an open concern, newest first. A card
opens 제안 보내기 2/4 with that store picked.

## Context

- Store concerns (ADR 0069, 0070) were only visible in the 탐색 「가게」 tab
  and in the proposal flow, so a student who opens the home never saw that
  an owner had just asked for help.
- `GET /explore/stores` already carries each store's open concern, and
  `sortStoresByConcern` orders stores by the concern's `updatedAt`.
- The backend has no "recent concerns" endpoint.

## Decision

- **Place**: right below 확인할 일 on the student home, also on the
  first-visit home (below the first-proposal guide, above 「이런 제안은
  어때요?」).
- **Data**: `useExploreStores` (all of `GET /explore/stores`), the stores
  with a concern, newest `updatedAt` first, at most three. While loading,
  on error, or with no concern at all the section is hidden and the rest of
  the home is unchanged.
- **Section**: 「새로 올라온 가게 고민」 with 「전체 ›」 → 탐색 「가게」
  (/student/explore/stores).
- **List** (`NewStoreConcerns`): one white bordered card with a row per
  store, divided by 1px lines: the store photo (32px), 「가게 · 업종 · M월
  D일」 (12px gray, date from `updatedAt`), the concern line in quotes (13px
  bold, up to two lines), and a small student-tint 「제안하기」 pill on the right.
  The whole row opens 제안 보내기 2/4 with `{ store }` in the route state,
  the same as 「제안하기」 in the 가게 tab.
- The concern is reference information only: the proposal is not filled
  from it (ADR 0069).

## Rationale

- Putting the newest concerns on the home makes the owner's 가게 고민 reach
  students without a tab change, which is the point of posting one.
- Reusing `GET /explore/stores` and the store-tab hand-off keeps one way to
  start a proposal for a store and needs no backend change.
- Three cards keep the home short; the 가게 tab lists the rest.

## Alternatives Considered

- **Only concerns from the last seven days**: the section would often be
  empty in the demo and early on. Newest first already shows what is new.
- **Opening 제안 보내기 1/4**: 1/4 is for choosing a store, and the store is
  already chosen here.
- **A horizontal card row**: it would look like 「이런 제안은 어때요?」 right
  below, which are examples, not real requests from owners.
- **One tinted card per store with the link under the line**: three cards
  took most of a screen. Rows in one card with the action on the right keep
  the section short.

## Agent Guidance

- The section reads all stores. If the store list grows large, ask the
  backend for a recent-concerns endpoint instead of filtering here.

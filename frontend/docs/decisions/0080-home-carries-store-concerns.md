# 0080. The home response carries the store concerns

## Status

Accepted. `GET /me/home` also returns the owner's open store concern and the
student's 「새로 올라온 가게 고민」, so each home loads with one list request
instead of two.

## Context

- Opening a home sent one request for the home and one more for the
  concern part: the owner home called `GET /owners/me/concern` (ADR 0070),
  and the student home called `GET /explore/stores` for every page, then
  kept three stores (ADR 0071). Measured on a production build: 4 requests
  per home, the 2 tab-dot requests included.
- The student request started only after the home had drawn (about
  0.5 s later on the local backend) and downloaded every store just to show
  three. ADR 0071 already asked for a backend list once the stores grow.
- The home response already marks a failed section with `null` and an empty
  one with `[]` (ADR 0064, 0065).

## Decision

- **Owner**: `GET /me/home` has `concerns`, the open concern in the same
  shape as `GET /owners/me/concern`. A store has at most one open concern,
  so it is `[]` or one item; `null` means only the concern failed. The home
  card reads it through `useOwnerHome` (`concern`); while the home loads, or
  when `concerns` is `null`, the card is hidden as before.
  `useOwnerConcern` stays for 내 정보 and the write screen.
- **Student**: `GET /me/home` has `newStoreConcerns`: up to three stores
  with an open concern in the student's 둘러보기 scope (the same scope as
  `GET /explore/stores`), newest `updatedAt` first, ties in store
  registration order. Each item has the same shape as a
  `GET /explore/stores` item, so `toExploreStore` maps both and the row still
  hands `{ store }` to 제안 보내기 2/4. `null` (failed) becomes an empty list
  and the section is hidden, as before. The 가게 tab still loads all stores.
- The backend reads the new concerns with one query over open concerns
  joined to stores (`StoreConcernRepository.findNewestOpen`), then the three
  stores and their category names.
- The concern parts do not count for the home's 「every section failed →
  500」 rule: a home where only the concern loaded is still an error.
- The tab-list cache (ADR 0077) is unchanged. Saving or resolving a
  concern is a write, so the cache is cleared and the next home request
  shows the change.

## Rationale

- One request per home removes a round trip from every home visit and the
  late section pop-in on the student home.
- Keeping the item shapes of the existing APIs means no new screen model
  and the same rows as the 가게 tab.
- A list for the owner's single concern keeps the home rule of `[]` for
  none and `null` for failed, so the card can tell 「no concern」 from 「could
  not load」.

## Alternatives Considered

- **A separate 「recent concerns」 endpoint**: still a second request on
  the student home.
- **`concern: object | null` for the owner**: `null` would mean both 「no
  concern」 and 「failed」.
- **Counting the concern parts in the 500 rule**: a home with only a
  concern card and every work section broken would look loaded.

## Agent Guidance

- Change the 「새로 올라온 가게 고민」 count in
  `HomeQueryService.NEW_CONCERN_SIZE`; the screen shows at most three
  (`NewStoreConcerns`).
- If `GET /explore/stores` items change shape, change `newStoreConcerns`
  with them; both use `StoreExploreResponse.Item` on the backend and
  `toExploreStore` on the frontend.
- Measured on production builds against the same local backend
  (둘러보기, requests after the screen opens): owner home 4 → 3
  (`GET /owners/me/concern` gone), student home 4 → 3 (`GET /explore/stores` gone).

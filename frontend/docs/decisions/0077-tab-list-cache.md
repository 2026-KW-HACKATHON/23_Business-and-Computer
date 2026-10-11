# 0077. Tab lists show the remembered answer and refresh in the background

## Status

Accepted. The lists that the 홈 · 탐색 · 내 활동 tabs load on every mount
are kept in a memory cache in `apiData`. A tab that opens again shows the
remembered list at once and asks again in the background; when the new
answer differs, the open screen redraws. Every write clears the cache, so the
user's own change always shows up.

## Context

- Each tab screen loads its lists when it mounts, so going 홈 → 탐색 → 홈
  waited for the 홈 list again (about 0.4–0.6 s on the deployed server).
- A cache must not hide a change the user just made: a 「글 올렸는데 안
  보여요」 bug is worse than a slow tab.
- Changes by the other side (a new 지원자, a submitted 초안) should not wait
  for a fixed time or a reload either.

## Decision

- `src/api/cache.ts` keeps answers of an allowlist of GETs: 홈, 탐색 lists,
  내 활동 lists, own profile and concern, specialties and business categories.
  Entries belong to the access token, so 둘러보기 role switches never share
  answers.
- Stale-while-revalidate: an answer younger than 5 minutes is returned at
  once; if it is older than 2 seconds the same GET is sent again in the
  background. Older than 5 minutes, the screen waits for the network.
- When a background answer differs, a revision number changes
  (`useCachedListRevision`). The list hooks of 홈 and 내 활동 have it in their
  load effect's dependencies, so they load again — from the cache, without
  showing loading — and draw the new list.
- When the unread notification count goes up (the bell polls every 20 s),
  every remembered list of the account is asked again in the background
  (`refreshCachedLists`), so an open 홈 shows the other side's change without
  switching tabs.
- Every non-GET `apiData` call (success or failure) clears the whole cache
  before it is sent and again when it settles. A GET that was in flight
  while the cache was cleared is not stored. Other tabs of the same browser
  clear too (`BroadcastChannel`).
- The same GET asked twice at once goes out once.
- Chat, notifications, unread counts, job details, submissions and payments
  are not cached.
- Answers are copied (`structuredClone`) so a screen cannot change the
  cached answer.

## Rationale

- Showing the remembered list keeps tab switches instant, and the background
  answer brings the other side's change one round trip later.
- Clearing everything on any write is coarse but needs no per-screen
  knowledge of which lists a write changes, so new write APIs are safe by
  default.

## Alternatives Considered

- **A fixed 30 second cache**: the other side's change could wait up to
  30 s or a reload.
- **A data library (React Query, SWR)**: a new dependency and a rewrite of
  the hand-written hooks for this one behavior.

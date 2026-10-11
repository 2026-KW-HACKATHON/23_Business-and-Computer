# 0078. One shared unread check for the bell dot and the 채팅 tab dot

## Status

Accepted. The app bar bell dot and the tab bar 「채팅」 dot come from one
call, `GET /me/notifications/unread-count`, which now also answers
`hasUnreadChat`. A shared store hands the answer to every tab screen, so a
tab switch no longer asks again.

## Context

- Every tab screen (`OwnerTabScreen`, `StudentTabScreen`) mounted its own
  `useChatUnread` (GET /me/chat-rooms) and `useNotificationUnread`
  (GET /me/notifications/unread-count). Each tab switch remounts the screen,
  so 홈 → 탐색 → 채팅 sent both calls on every switch, and two 20 second
  timers polled them.
- The 채팅 tab loaded GET /me/chat-rooms twice: once for the list and once
  for the dot.
- The chat dot only needs "is any room unread", but GET /me/chat-rooms builds
  the whole list (other side, submissions, last messages) for it.
- Measured on a production build (둘러보기 accounts): 홈 4 calls,
  탐색 5, 채팅 3 on every visit, plus 2 calls every 20 seconds.

## Decision

- Backend: `GET /me/notifications/unread-count` answers
  `{ unreadCount, hasUnreadChat }`. `hasUnreadChat` uses the same rooms as
  GET /me/chat-rooms (an owner's jobs, a student's selected jobs) and one
  `EXISTS` query over messages from the other side after the read position.
  Users without a profile or role get `false`. The field is added, so older
  clients keep working.
- Frontend: `src/features/notification/lib/unreadDots.ts` keeps the latest
  answer for the account (access token) and `useUnreadDots` reads it with
  `useSyncExternalStore`.
  - While any tab screen is open it loads when the last answer is 20 seconds
    old, every 20 seconds while the browser tab is visible, when the browser
    tab becomes visible again, and after each notification or chat read
    settles (after pending reads, as before). It stops while hidden.
  - When the last tab screen leaves, it stops one tick later, so a tab switch
    (old screen out, new screen in) keeps the same timer and answer.
  - A read that settles while no tab screen is open (in a chat room or on
    the 알림 screen) drops the answer, so the next tab screen loads at once.
  - A different access token (a 둘러보기 role switch, another account)
    drops the dots and loads again.
  - A failed load hides both dots. A higher unread count still refreshes the
    remembered tab lists (ADR 0077).
- `useChatUnread` and `useNotificationUnread` are removed. The chat list keeps
  its own GET /me/chat-rooms.

## Rationale

- One call and one timer for two dots: tab switches send 2 fewer calls
  (홈 4 → 2, 탐색 5 → 3, 채팅 3 → 1) and polling halves (2 → 1 per 20 s).
- An `EXISTS` query stops at the first unread message instead of building
  the chat list.
- Dots can be up to 20 seconds behind a change by the other side, the same
  as the polling before; the user's own reads still update them at once.

## Alternatives Considered

- **A new endpoint (e.g. GET /me/badges)**: the backend rules prefer
  extending the existing notification controller, and the unread count was
  already the bell's call.
- **Only sharing on the frontend**: removes the per-switch calls but keeps
  two polls and the full chat list for one boolean.

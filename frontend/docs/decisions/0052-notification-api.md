# 0052. Owner and student notifications read the notification API

## Status

Accepted. The owner and student 알림 screens and the app bar bell dot read
the backend through `src/features/notification`. The sample notifications
are removed.

## Context

The backend (dev) has, for any logged-in user:

- GET /me/notifications `?size` (default 20, 1–100) `&cursor` (none or
  empty = first page) → `{ items, nextCursor }`. Items are newest first
  (`createdAt`, then `id`) whether read or not; `nextCursor` is null on the
  last page. An item is `{ id, type, title, body, targetType, targetId,
  readAt (null = unread), createdAt }`; `title` and `body` are finished
  sentences, and times are UTC without an offset. Reading the list does not
  mark anything read.
- GET /me/notifications/unread-count → `{ unreadCount }` over every type.
- PUT /me/notifications/{id}/read → `{ notificationId, readAt }`; a repeat
  keeps the first `readAt`.
- PUT /me/notifications/read → `{ updatedCount }`, 0 included.
- Errors: 401; 400 COMMON_400 (`size` out of range or a broken cursor); 404
  NOTIFICATION_404 (no such notification, or another user's).
- `type`: JOB_DRAFT_SUBMITTED, PROPOSAL_RECEIVED, JOB_APPLICATION_RECEIVED,
  CHAT_MESSAGE_RECEIVED, PAYMENT_COMPLETED, JOB_COMPLETED,
  PROPOSAL_LIKE_MILESTONE_REACHED. `targetType`: JOB, PROPOSAL, CHAT_ROOM,
  PAYMENT; `targetId` is a string of up to 26 characters.
- No backend code creates a notification: there is no producer, no
  scheduler, and no demo sample, so the list is empty. Issue #234 (open,
  2026-10-07), item 5 「알림 생성」, asks the backend which events create
  notifications; it has no reply.
- Team decisions on notifications: new chat messages are not in the
  notification list (the chat tab dot covers them); an application job
  sends the student one 「선정」 notification that opens the chat room; a
  proposal job sends the owner 「작업 시작」 when the student starts it; like
  notifications at 10, 30, and 50 likes, worded 「학생 손님」; deadline
  reminders 2 days before the draft and 1 day before the final deadline. The
  backend has none of these as types or producers.

## Decision

- **Shared feature** `src/features/notification`:
  - `src/features/notification/api/notificationApi.ts` calls the four APIs
    through `apiData`; `type` and `targetType` also accept unknown strings.
  - `src/features/notification/lib/notifications.ts` turns items into
    screen items (`read`, ISO `createdAt` from the UTC value with
    `isoOfUtc` in `src/lib/date.ts`), groups them into 오늘 / 어제 / 이전,
    picks the icon by type (🔔 for an unknown type), and hides
    CHAT_MESSAGE_RECEIVED.
  - `src/features/notification/hooks/useNotifications.ts` loads the first
    page on entry and the next page with `loadMore`; marks one or all read
    on screen first and then on the server; reloads the list when 「모두
    읽음」 fails; sends 401 to /login.
  - `src/features/notification/hooks/useNotificationUnread.ts` drives the
    bell dot.
- **Screens** (`OwnerNotificationsPage`, `StudentNotificationsPage`): groups
  오늘 / 어제 / 이전 with `NotificationRow`; the next page loads when the end
  of the list comes near (`useLoadMoreSentinel` in
  `src/features/explore/hooks/useLoadMoreSentinel.ts`);
  「모두 읽음」 in the app bar once loaded; 「아직 알림이 없어요」 when nothing
  is left to load. Loading and errors use `LoadNotice` (「알림을 불러오는
  중이에요」 · 「알림을 불러오지 못했어요」, and 「더 불러오는 중이에요」 ·
  「더 불러오지 못했어요」 for the next page) with 「다시 시도」.
- **Tap**: an unread item is marked read (PUT one), then the screen from
  `notificationPath` in `src/features/owner/lib/notifications.ts` or
  `src/features/student/lib/notifications.ts` opens. The type decides first,
  then `targetType`; an unknown type or target, or an id that is not a
  positive number for JOB · PROPOSAL, stays on the list.

  | | 사장님 | 학생 |
  |---|---|---|
  | JOB_DRAFT_SUBMITTED (JOB) | 작업 확인 /owner/works/{id}/check | — |
  | JOB_APPLICATION_RECEIVED (JOB) | 지원자 목록 /owner/requests/{id}/applicants | — |
  | JOB_COMPLETED (JOB) | 결과물 보기 /owner/works/{id}/result | 내 결과물 /student/works/{id}/result |
  | other JOB | 의뢰서 상세 /owner/requests/{id} | 초안 제출 /student/works/{id}/submit (moves to the job's stage) |
  | PROPOSAL | 받은 제안 상세 /owner/proposals/{id} | 보낸 제안 상세 /student/proposals/{id} |
  | CHAT_ROOM | 채팅방 /owner/chats/{id} | 채팅방 /student/chats/{id} |
  | PAYMENT | 결제 내역 /owner/me/payments | 정산 내역 /student/me/settlements |

- **Bell dot**: `GET /me/notifications/unread-count` when a tab-bar screen
  opens, when the tab becomes visible, after each read (one or all), and
  every 20 seconds while visible; it stops while hidden and hides on
  failure. `src/features/notification/lib/readSync.ts` makes the dot load
  after pending reads settle. The count is the server's, so unread
  CHAT_MESSAGE_RECEIVED notifications light the dot although the list does
  not show them.

## Rationale

- One feature for both roles keeps paging, reading, and the dot the same;
  only the destinations differ by role.
- Marking read on screen first keeps the tap responsive; the dot reloads
  from the server after the request settles.

## Alternatives Considered

- Counting unread items on the client to leave chat notifications out of
  the dot: rejected, the client sees only the loaded pages.

## Agent Guidance

- New types or targets need an entry in `isKnownNotificationType` and the
  two `notificationPath` functions; until then they show without a
  destination.

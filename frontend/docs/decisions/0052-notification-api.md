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
- `type` (backend `NotificationType`, 21): JOB_DRAFT_SUBMITTED,
  PROPOSAL_RECEIVED, JOB_APPLICATION_RECEIVED, CHAT_MESSAGE_RECEIVED,
  PAYMENT_COMPLETED, JOB_COMPLETED, PROPOSAL_LIKE_MILESTONE_REACHED,
  JOB_APPLICATION_SELECTED, JOB_APPLICATION_REJECTED, PROPOSAL_REJECTED,
  PROPOSAL_CANCELLED, PROPOSAL_ACCEPTED, JOB_STARTED, JOB_REVISION_REQUESTED,
  JOB_REVISION_SUBMITTED, JOB_REVIEW_REQUESTED, JOB_REVIEW_RECEIVED,
  JOB_RECRUITMENT_CANCELLED, JOB_CANCELLED_BY_OWNER, PAYMENT_REFUNDED,
  PAYMENT_SETTLED. `targetType`: JOB, PROPOSAL, CHAT_ROOM, PAYMENT;
  `targetId` is a string of up to 26 characters.
- The backend creates the last 18 through `NotificationEventFactory`; the
  first three of the second line (CHAT_MESSAGE_RECEIVED, PAYMENT_COMPLETED,
  JOB_COMPLETED) stay only for stored notifications. Targets: JOB (job id)
  for the job events, PROPOSAL_ACCEPTED (the job the payment created, not the
  proposal), and PAYMENT_SETTLED; PROPOSAL (proposal id) for the proposal
  events; CHAT_ROOM (room id) for JOB_APPLICATION_SELECTED, JOB_STARTED, and
  JOB_CANCELLED_BY_OWNER; PAYMENT (payment id) for PAYMENT_REFUNDED. Likes
  notify at 10, 30, and 50. There are no deadline reminders.
- `GET /jobs/{id}` returns a job in any status (cancelled ones included) to
  users of the same demo scope. `GET /proposals/{id}` returns a cancelled
  proposal only to the student who sent it (404 for the owner).

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
  `notificationPath` in `src/features/owner/lib/notifications.ts`, or from
  `resolveNotificationPath` in `src/features/student/lib/notifications.ts`,
  opens. The type decides first when its `targetType` is the expected one,
  then `targetType`; an unknown type or target, or an id that is not a
  positive number for JOB · PROPOSAL, stays on the list.

  | type (targetType) | 사장님 | 학생 |
  |---|---|---|
  | JOB_APPLICATION_RECEIVED (JOB) | 지원자 목록 /owner/requests/{id}/applicants | other JOB |
  | JOB_DRAFT_SUBMITTED · JOB_REVISION_SUBMITTED (JOB) | 작업 확인 /owner/works/{id}/check | other JOB |
  | JOB_REVIEW_REQUESTED (JOB) | 후기 작성 /owner/works/{id}/review (a job with a review goes to 남긴 후기 /owner/works/{id}/review/view) | other JOB |
  | JOB_COMPLETED (JOB) | 결과물 보기 /owner/works/{id}/result | 내 결과물 /student/works/{id}/result |
  | JOB_APPLICATION_REJECTED · JOB_RECRUITMENT_CANCELLED (JOB) | other JOB | 의뢰서 /student/requests/{id}/full |
  | JOB_REVISION_REQUESTED (JOB) | other JOB | 수정 요청 확인 /student/works/{id}/revision |
  | JOB_REVIEW_RECEIVED (JOB) | other JOB | 받은 후기 /student/works/{id}/review |
  | PAYMENT_SETTLED (JOB) | other JOB | 정산 내역 /student/me/settlements |
  | PROPOSAL_ACCEPTED (JOB) | other JOB | 작업 시작 /student/proposals/{proposalId}/start for the sent proposal whose `jobId` is the target (GET /me/proposals); none found or the list fails → 내 활동 › 보낸 제안 /student/activity?tab=proposals |
  | other JOB | 의뢰서 상세 /owner/requests/{id} | 초안 제출 /student/works/{id}/submit (moves to the job's stage) |
  | PROPOSAL (PROPOSAL_RECEIVED · _LIKE_MILESTONE_REACHED · _REJECTED · _CANCELLED) | 받은 제안 상세 /owner/proposals/{id} | 보낸 제안 상세 /student/proposals/{id} |
  | CHAT_ROOM (JOB_APPLICATION_SELECTED · JOB_STARTED · JOB_CANCELLED_BY_OWNER · CHAT_MESSAGE_RECEIVED) | 채팅방 /owner/chats/{id} | 채팅방 /student/chats/{id} |
  | PAYMENT (PAYMENT_REFUNDED · PAYMENT_COMPLETED) | 결제 내역 /owner/me/payments | 정산 내역 /student/me/settlements |

- **Destinations and their states**: 의뢰서 (student) opens rejected, matched,
  and cancelled jobs; 수정 요청 확인 and 초안 제출 move to the job's current
  stage and show 「진행 중인 작업이 아니에요」 for a job not in progress; the
  chat room opens for a job the owner cancelled (the selected student stays
  on the job). 받은 제안 상세 for PROPOSAL_CANCELLED shows 「없음」, because the
  backend returns a cancelled proposal only to its student.
- **Icons**: job events the request icon, proposal events the proposal icon
  (cancelled and rejected ones too), 결제 · 환불 · 정산 💳, 후기 ⭐, 완료 ✅,
  chat 💬, an unknown type 🔔.
- **Check**: `npm run test:notifications`
  (`scripts/check_notification_paths.mjs`, part of `npm run check`) loads the
  routing modules through Vite and checks every type for both roles, the
  PROPOSAL_ACCEPTED fallbacks, and the unknown and invalid-id cases.
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

- A new type needs an entry in `TYPE_ICONS`
  (`src/features/notification/lib/notifications.ts`), a case in the two
  routing files when its screen is not the `targetType` default, and rows in
  `scripts/check_notification_paths.mjs`; without the `TYPE_ICONS` entry it
  shows with 🔔 and no destination.

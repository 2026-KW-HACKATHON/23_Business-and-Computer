# 0034. Owner and student chat read the chat API and poll for new messages

## Status

Accepted. The chat list and chat room for owners and students read and write
the backend through `src/features/chat`. Sending photos and files is in
ADR 0050.

## Context

The backend (dev) has these chat endpoints (all need login; only the job's
owner and its selected student may use a room):

- GET /me/chat-rooms → `{ count, rooms }`. Rooms are sorted by latest message,
  and empty rooms by creation. A room has `roomId` (string), `jobId`,
  `jobTitle`, `counterpartName` (store name for a student, student name for an
  owner), `counterpartProfileImageUrl`, `jobStatus` (OPEN · AWAITING_START ·
  MATCHED · CLOSED · CANCELLED), `deadlineType` (DRAFT · FINAL, only for
  a MATCHED job) and `deadlineDate`, `submissionReviewStatus` of the latest
  submission, `budget`, `revisionCount`, `draftDeadline`, `finalDeadline`,
  `applicationSummary` · `applicationWorkPlan` · `applicationDeliveryMethod`
  (the selected application; null for a job started from a proposal),
  `lastMessage { type, preview, createdAt }`, and `unreadCount`.
- GET /chat-rooms/{roomId} → one room.
- GET /chat-rooms/{roomId}/messages → `{ viewerUserId, messages }`, the whole
  history in id order with no paging. `viewerUserId` is the logged-in user's
  `User.id`, on every history answer; the one-message and send answers do not
  carry it. A message has `id` (number), `roomId`, `clientMessageId`,
  `senderUserId`, `type` (TEXT · IMAGE · FILE), `content` (the text, or a
  view URL valid for 15 minutes), `attachmentName`, `contentExpiresAt`, and
  `createdAt`.
- GET /chat-rooms/{roomId}/messages/{messageId} → one message with a new view
  URL.
- PUT /chat-rooms/{roomId}/read `{ lastReadMessageId }`. The read position
  only moves forward.
- POST /chat-rooms/{roomId}/messages `{ clientMessageId (UUID), content
  (≤ 5000) }`. Sending the same `clientMessageId` again returns the stored
  message.
- Attachment upload: POST …/attachments/uploads, then
  POST …/messages/attachments (ADR 0050).
- Errors: 401, CHAT_403, CHAT_ROOM_404, CHAT_MESSAGE_404, CHAT_MESSAGE_409
  (same `clientMessageId` with other content), COMMON_400.
- A room is made per job: when the owner's KakaoPay payment for an applicant
  is approved, or when the student starts a proposal job (POST
  /jobs/{jobId}/start).
- User ids (`viewerUserId`, `senderUserId`) are strings (`users.user_id`,
  26-character ULID).
- There is no WebSocket or SSE. There are no system messages, file sizes, or
  work kinds.

## Decision

- **Shared feature** `src/features/chat`:
  - `src/features/chat/api/chatApi.ts` calls through `apiData`;
  - `src/features/chat/lib/chatRoom.ts` makes the status text, the plan, and
    which work actions show;
  - `src/features/chat/lib/messages.ts` turns responses into screen messages,
    merges reloads, and maps failures;
  - `src/features/chat/hooks/useChatRooms.ts` and
    `src/features/chat/hooks/useChatRoom.ts` load, poll, read, and send;
  - `src/features/chat/hooks/useScrollToLatest.ts` keeps the room scrolled to
    the latest message.

  The four screens
  (`OwnerChatsPage`, `OwnerChatRoomPage`, `StudentChatsPage`,
  `StudentChatRoomPage`) use it.
- **Routes**: /owner/chats/:roomId and /student/chats/:roomId. The room screen
  is keyed by `roomId`.
- **Mine or partner**: a stored message is mine when `senderUserId ===
  viewerUserId` (string comparison). The hook keeps the `viewerUserId` of the
  last history answer and also uses it for send answers and refreshed
  attachments, which do not carry it. A sending or failed bubble is always
  mine. Until the first history answer arrives (or if one lacks
  `viewerUserId`), only messages this screen sent are mine.
- **Refresh**: the list loads on entry and again when the tab becomes visible
  (a failed refresh keeps the current list). The room loads the room and the
  history together, then reloads the history every 3 seconds while
  `document.visibilityState` is visible. When the tab becomes visible again it
  reloads the history at once and the room card too. A merge keeps a view URL
  that is valid for more than one more minute, so photos are not downloaded on
  every reload.
- **Read**: whenever the last stored message id grows (entering the room or
  receiving a message), PUT /read with that id. A failed read is sent again
  with the next new message. `src/features/chat/lib/readSync.ts` tracks each
  read request: the chat list and the tab dot load after the pending reads
  settle and load again whenever one settles, so the unread counts and the
  dot update right after reading. When loads overlap, only the last answer is used.
- **Tab dot** (`useChatUnread`, owner and student): true when any room in
  GET /me/chat-rooms has `unreadCount` above 0. `OwnerTabScreen` and
  `StudentTabScreen` pass it to `MainTabScreen` → `TabBar`, which shows a
  10px dot in the role color (owner yellow, student purple) with a 2px white
  ring at the top right of the 「채팅」 icon, like the dot on the app bar's
  알림 bell. It loads when a tab-bar screen opens, when the tab becomes
  visible, after each read, and every 20 seconds while visible; it stops
  while hidden. A failed load hides the dot. The dot is `aria-hidden`; the tab
  button carries visually hidden text, so a screen reader reads 「채팅, 안 읽은
  메시지 있음」.
- **Send**: `newClientMessageId()` makes `clientMessageId` as a UUID v4 with
  `crypto.randomUUID()`, or with `crypto.getRandomValues` where `randomUUID`
  is missing (pages not on https or localhost). The bubble shows at
  once with 「보내는 중」; success swaps in the stored message; failure shows
  「보내지 못했어요」 and 「다시 보내기」, which resends the same
  `clientMessageId`. A pending or failed bubble that turns up in the reloaded
  history is replaced by the stored one. The input takes up to 5000
  characters. The 「+」 button left of the input sends a photo or file
  (ADR 0050).
- **Scroll**: the room opens at the latest message. When messages are added
  it scrolls to the bottom only if the view was within 80px of the bottom or
  the newest message is one being sent from this screen; while reading older
  messages further up it stays put. There is no jump-to-latest button.
- **Names**: owner screens show the student with `studentTitle` (「김광운
  학생」; a name already ending in 학생, like 「데모 학생」, is left as is).
  Student screens show 「{store name} 사장님」.
- **Photos and files**: a photo shows as a thumbnail and a file as its name,
  both opening the view URL in a new tab. If `contentExpiresAt` has passed, a
  blank tab opens first, GET of the one message fetches a new URL, and the tab
  goes there; failure closes the tab and alerts 「파일을 열지 못했어요. 잠시 후
  다시 시도해 주세요」. A message without `content` shows 「열 수 없는
  파일이에요」.
- **Room card**: `ChatWorkCard` with the stage, 「이력 상세보기 ›」, the
  action, and 「문제가 있나요?」 (ADR 0045). The work is a proposal when the room's `jobId` is the
  `jobId` of a received proposal (owner, GET /me/received-proposals) or a sent
  proposal (student, GET /me/proposals), and a request otherwise or when that
  list fails to load (`useProposalJobIds` in each feature).
- **List status**: from `jobStatus` (CLOSED → 완료, CANCELLED → 성사되지 않음)
  or, for a matched job, from the review status and deadline: 「초안 만드는 중
  (~M월 D일)」, 「결과물을 확인해 주세요」 · 「사장님이 확인 중」. 수정안 is when
  `deadlineType` is FINAL or a revision was requested, and its date is
  `finalDeadline`, because `deadlineType` turns FINAL only once a draft is
  approved and a revising job still has DRAFT with the draft deadline.
  Unknown → the line is hidden.
- **List rows**: last message 「사진」, 「파일 · name」, or the text; an empty
  room says 「아직 메시지가 없어요」 with no time.
- **Errors**: 401 → /login. CHAT_403 → alert 「이 채팅방에는 들어갈 수
  없어요」, CHAT_ROOM_404 → alert 「채팅방을 찾을 수 없어요」, then the chat
  list. A failed first load shows `LoadNotice` 「채팅방을 불러오지 못했어요」 ·
  「채팅 목록을 불러오지 못했어요」 with 「다시 시도」. Failed background reloads
  are ignored.

## Rationale

- Polling every 3 seconds needs no package and matches a two-person room;
  stopping while hidden avoids requests nobody sees.
- Reusing `clientMessageId` makes a resend safe even when the first request
  was stored but its answer was lost.
- Deciding the work actions from `jobStatus` keeps a cancel button off a
  finished or cancelled job; status lines the room cannot decide are hidden.

## Alternatives Considered

- STOMP over WebSocket (the @stomp/stompjs package): rejected, the backend has no
  WebSocket endpoint.
- Telling sides apart by remembering `senderUserId` from a send answer:
  rejected, it fails before the first send and after a reload.

## Agent Guidance

- `viewerUserId` and `jobStatus` are typed optional in
  `src/features/chat/api/chatApi.ts`; `viewerUserId` sides a bubble, and
  `jobStatus` shows the work actions and the 완료 · 성사되지 않음 lines.
- 「작업 취소」 passes the room's numeric `jobId`, so `OwnerWorkCancelPage`
  cancels through the API (ADR 0035).
- The owner home and 내 활동 「문의하기」 · 「채팅하기」 and the student 수정
  요청 확인 「문의하기」 open the chat list, not a room.

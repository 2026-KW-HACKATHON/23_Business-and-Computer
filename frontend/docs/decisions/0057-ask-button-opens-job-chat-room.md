# 0057. 「문의하기」 opens the job's chat room

## Status

Accepted. The student 수정 요청 확인 and 내 활동 › 진행 중 「문의하기」 and
the owner 내 활동 › 진행 중 「문의하기」 open the chat room of that job.

## Context

- Each job has one chat room between its owner and its student.
  GET /me/chat-rooms lists the viewer's rooms, each with its `jobId`
  (ADR 0034).
- 「문의하기」 sits on one job, but it opened the chat list, so the user had
  to find the room again.

## Decision

- `useOpenJobChat` (`src/features/chat/hooks/useOpenJobChat.ts`) takes the
  role's chat room path and chat list path and returns a function of a
  `jobId`. It reads GET /me/chat-rooms and opens the room with the same
  `jobId`.
- When no room has that `jobId` or the list fails to load, it opens the chat
  list.
- A press while the list is loading does nothing, and the result does not
  navigate after the screen has closed.
- Used by `src/pages/StudentJobStagePages.tsx` (수정 요청 확인),
  `src/pages/StudentActivityPage.tsx`, and `src/pages/OwnerActivityPage.tsx`.
  This replaces 「문의하기 (채팅 목록)」 in ADR 0032 and ADR 0035 and the
  chat list line in ADR 0034's Agent Guidance.

## Rationale

- The backend has no endpoint from a job to its room; the room list already
  carries `jobId`, and both roles use the same list.
- One hook in `src/features/chat` keeps the lookup and the fallback the same
  for both roles.

## Alternatives Considered

- Keeping the chat list: one more tap and a search for the right room.
- Loading the rooms with each activity list to link rooms directly: an
  extra request on every visit for a button that is rarely pressed.

## Agent Guidance

- A new button that asks about one job uses `useOpenJobChat` with
  `STUDENT_PATHS` or `OWNER_PATHS` `chat` and `chats`.
- `findJobChatRoomId` returns the room id, or `undefined` when none matches
  or the load fails.

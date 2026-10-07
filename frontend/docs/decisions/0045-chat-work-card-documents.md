# 0045. The chat work card opens the work's documents

## Status

Accepted. The work card above each chat room shows the current stage, the
terms, one action button, 「이력 상세보기 ›」 on the title row, and
「문제가 있나요? ›」 at the bottom, which opens 작업 취소 · 문제 신고 beside
it. 「이력 상세보기 ›」 opens 「작업 이력」: a flow bar and the work's
documents in order, each opening its own screen. The chat has no 작업계획서
sheet.

## Context

- The card had only 「작업계획서 보기」, so the documents that pile up as the
  work goes on (초안, 수정 요청, 수정안, 결과물, 후기, 취소 내역) could not be
  opened from the chat, and some links opened sheets while others opened
  screens.
- The status line already says where the work is (「수정안 만드는 중」), so
  the card does not repeat it with document labels.
- The room (GET /me/chat-rooms, GET /chat-rooms/{id}) says whether a
  submission waits, not whether it is a draft or a revision. The matched lists
  (GET /me/jobs?status=MATCHED) do: the owner list has `submissionType`, the
  student list the last submission's type and review status.
- The backend returns only the current submission (owner: GET
  /jobs/{id}/submission; student: GET /jobs/{id}/submissions/latest) and,
  after completion, the result. GET /jobs/{id}/submissions/latest, which
  carries the revision request, is open to the assigned student only. Past
  drafts and the owner's own review cannot be read yet (requested: owner
  access to /submissions/latest, GET /jobs/{id}/submissions).
- The server refuses 작업 취소 once the job has any submission.

## Decision

- **Stage** (`chatWorkStageOf` in `src/features/chat/lib/workDocs.ts`):
  CLOSED → completed, CANCELLED → notConcluded. A matched job takes the
  matched list's stage (drafting, revising, or submitted with
  `revisionSubmitted` → draftArrived · revisionArrived) and uses the room
  until the list loads.
- **Documents** (`chatWorkDocs`), guessed from the stage until the history
  API exists: start → 초안 → 수정 요청 → 수정안, start → 결과물 (→ 후기 for
  the student), or start → 취소 내역. Both roles use the same labels: 의뢰서
  (제안서 for a proposal job), 초안, 수정 요청, 수정안, 결과물, 후기, 취소 내역.
- **Card** (`ChatWorkCard`, both chat rooms):
  - the title row: the work icon, the title, and 「이력 상세보기 ›」 (작업
    이력);
  - the status: 「초안 만드는 중, M월 D일까지 도착 · 제출」, 「초안이 도착했어요,
    확인해 주세요」 / 「사장님이 초안을 확인하고 있어요」, the same for 수정안,
    「완료된 작업이에요」, 「성사되지 않은 작업이에요」;
  - the terms: 작업비, 수정 n회, 최종 마감;
  - the action: owner 「초안 확인하기」 · 「수정안 확인하기」 (작업 확인) and
    「후기 남기기」 once the closed list says no review yet; student 「초안
    제출하기」 and 「수정안 작성하기」;
  - 「문제가 있나요? ›」 under a gray line, in the 12px gray of the 내 활동
    card. A tap shows the text buttons beside it and turns the arrow to ‹:
    작업 취소 (owner, matched with no submission yet, `canCancelChatWork`)
    and 문제 신고 (matched). The row is hidden when neither applies.
- **Where documents go** (`ownerWorkDocPath`, `studentWorkDocPath`):

  | 서류 | 사장님 | 학생 |
  | --- | --- | --- |
  | 의뢰서 · 제안서 | 보낸 의뢰 · 받은 제안 상세 | 의뢰서 전체 보기 · 보낸 제안서 상세 |
  | 초안 | 작업 확인 while it waits | 제출한 초안 while it waits or while revising |
  | 수정 요청 | 보낸 수정 요청 while revising | 수정 요청 확인 while revising |
  | 수정안 | 작업 확인 while it waits | 제출한 수정안 while it waits |
  | 결과물 | 지난 결과물 보기 | 내 결과물 |
  | 후기 | none yet | 받은 후기 |
  | 취소 내역 | 성사되지 않은 작업 | 성사되지 않은 작업 |

- **작업 이력** (/owner/works/:id/history and /student/works/:id/history,
  `ChatWorkHistory`): the work (title, 작업 중 · 완료 · 성사되지 않음, the other
  side, 작업비 · 수정 n회), a flow bar (hidden when not concluded), and one row
  per document with a gray line and no date. Rows without a screen are gray
  without a ›, and 「지난 서류는 아직 다시 열 수 없어요」 shows under the list.
  The room comes from GET /me/chat-rooms by job id.
- **보낸 수정 요청** (/owner/works/:id/revision/sent,
  `OwnerRevisionSentPage`), laid out like 수정 요청: the work box (「박지은
  학생, 9월 23일 수정 요청, 수정 1/1」), 「이렇게 고쳐 달라고 했어요」 with
  「추가 자료나 질문은 채팅으로 보내 주세요.」, 「요청 내용」 in an input-like box, 참고 사진,
  and while revising 「학생은 최종 마감(M월 D일)까지 수정안을 보내요」; 「확인」
  goes back. It reads GET /jobs/{id}/submissions/latest (`useLatestJobSubmission`).
  Until the server opens it to owners (an owner gets 403 today, read as not
  found instead of the forbidden alert), the page keeps this layout and the
  「요청 내용」 box shows 「보낸 수정 요청은 곧 여기서 볼 수 있어요」 in grey.
- **의뢰서 전체 보기 · 작업 중** (student, `applied` ACCEPTED): the room's
  status line, a flow bar at the current stage (작업 중 · 확인 중), the store,
  the terms, 「내 작업계획서」 from the room's application, then 할 일 ·
  맡기고 싶은 일 · 참고 자료, with 「확인」 in the footer instead of the
  selection guide. Without a room it shows the job status and no plan.
- **내 활동** (owner, 진행 중): 작업 취소 shows only while drafting, and a gray
  line sits above the 「문제가 있나요?」 row.
- `useProposalJobIds` (owner and student) maps a job id to its proposal id,
  so 「제안서」 opens that proposal.

## Rationale

- The status line and one history link keep the card short; every document
  is two taps away and opens a screen.
- 「문제가 있나요?」 looks like the 내 활동 card and keeps 작업 취소 · 문제 신고
  out of the way until asked.
- The same names on both sides, and every document opens a screen, so the
  chat never mixes sheets and screens.

## Alternatives Considered

- Document chips on the card: they repeated the status line and did not fit
  a phone.
- A 「⋯」 menu for 작업 취소 · 문제 신고: hard to notice and unlike the 내 활동
  card.
- Dates on the history rows: the backend has dates only for the newest
  documents, so rows would look uneven.

## Agent Guidance

- Once owners can read GET /jobs/{id}/submissions/latest, 보낸 수정 요청 works
  as is. When GET /jobs/{id}/submissions or an owner review read ships, add the
  past draft, past revision request, past revision, and owner review screens,
  fill the missing paths in `ownerWorkDocPath` and `studentWorkDocPath`, and
  build the documents from the history instead of the stage.
- A new card action for trouble goes in the `trouble` list, not in a new row.

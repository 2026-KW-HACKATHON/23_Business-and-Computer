# 0045. The chat work card opens the work's documents

## Status

Accepted. The work card above each chat room shows the current stage, the
terms, one action button, 「이력 상세보기 ›」 on the title row, and
「문제가 있나요? ›」 at the bottom, which opens 작업 취소 · 문제 신고 beside
it. 「이력 상세보기 ›」 opens 「작업 이력」: a flow bar and every document of
the work in order, past drafts, revision requests, revisions, and the owner's
review included, each opening its own screen. The chat has no 작업계획서
sheet.

## Context

- The card had only 「작업계획서 보기」, so the documents that pile up as the
  work goes on (초안, 수정 요청, 수정안, 결과물, 후기, 취소 내역) could not be
  opened from the chat, and some links opened sheets while others opened
  screens.
- The status line already says where the work is (「수정안 만드는 중」), so
  the card does not repeat it with document labels.
- The room (GET /me/chat-rooms, GET /chat-rooms/{id}) carries the job status,
  the deadline type, the last submission's `submissionReviewStatus`,
  `submissionType` (DRAFT · REVISION), and `revisionNumber`, and the
  `proposalId` of a job started from a proposal.
- GET /jobs/{id}/submissions → `{ submissions [{ submissionId,
  submissionType, revisionNumber, fileUrls, files, message, reviewStatus,
  submittedAt, revisionRequest { message, referenceImageUrls, requestedAt }
  }] }` gives the owner and the assigned student every draft and revision with
  the revision request it received, in any job state (an empty list before the
  first submission).
- GET /jobs/{id}/review gives the owner the review they wrote and the student
  the review they received; REVIEW_404 otherwise.
- The server refuses 작업 취소 once the job has any submission.
- Figma: 「작업 이력 (사장님)」 (node 3437-5140), 「작업 이력 (학생)」 (node
  3437-8611), 「작업 확인 · 지난 초안 (읽기 전용)」 (node 3423-8579), 「보낸 수정
  요청 보기 (사장님)」 (node 3386-8297), 「남긴 후기 보기 (사장님)」 (node
  3423-8679), and the student 「제출한 초안 보기」 · 「제출한 수정안 보기」 ·
  「수정 요청 확인」.

## Decision

- **Stage** (`chatWorkStageOf` in `src/features/chat/lib/workDocs.ts`), from
  the room alone: CLOSED → completed, CANCELLED → notConcluded; a waiting
  submission → draftArrived or revisionArrived by `submissionType`; otherwise
  drafting, or revising after a revision request or once the final deadline
  applies. A job is a proposal job when the room has `proposalId`.
- **Documents from the history** (`chatWorkEntriesFromSubmissions`): 의뢰서
  (제안서 for a proposal job), then each submission in order as 초안 or 수정안,
  each followed by its 수정 요청 when it received one. A completed work ends
  with 결과물 (standing for the approved last submission) and 후기; a work that
  did not go through ends with 취소 내역. With two or more revision requests,
  수정 요청 and 수정안 carry the round, 「수정 요청 1」「수정안 1」「수정 요청 2」….
  While the work goes on, the last row (the waiting submission, or the request
  being worked on) is the current document; every other row is past.
- **Before the history loads** (`chatWorkDocs`, `chatWorkEntries`): rows
  guessed from the stage and the room's `revisionNumber`; past rows have no
  screen and are grey. If the history fails, 「지난 서류를 불러오지 못했어요」
  shows under the list. Both roles use the same labels.
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
  | 초안 · 수정안 (지금) | 작업 확인 | 제출한 초안 · 수정안 |
  | 초안 · 수정안 (지난) | 지난 초안 · 지난 수정안 | 제출한 초안 · 수정안 (읽기 전용) |
  | 수정 요청 (지금) | 보낸 수정 요청 | 수정 요청 확인 |
  | 수정 요청 (지난) | 보낸 수정 요청 (그 회차) | 받은 수정 요청 (읽기 전용) |
  | 결과물 | 지난 결과물 보기 | 내 결과물 |
  | 후기 | 남긴 후기 | 받은 후기 |
  | 취소 내역 | 성사되지 않은 작업 | 성사되지 않은 작업 |

- **작업 이력** (/owner/works/:id/history and /student/works/:id/history,
  `ChatWorkHistory`): the work (title, 작업 중 · 완료 · 성사되지 않음, the other
  side, 작업비 · 수정 n회), a flow bar (hidden when not concluded), and one row
  per document with a gray line and no date. The room comes from GET
  /me/chat-rooms by job id. The owner's 후기 row shows once the owner reviewed
  (the closed list's `reviewed`, or a review left in this session); the
  student's always shows for a completed work.
- **지난 초안 · 지난 수정안** (owner, /owner/works/:id/submissions/:submissionId,
  `OwnerPastSubmissionPage`): 「신고」 at the top right, the work box (「박지은
  학생 · 초안 도착 9월 22일 · 수정 1/2」), the flow bar at the work's current
  stage, 「이 초안에 수정을 요청했어요」, 원본 파일 with 받기, the student's
  message, and 「확인」.
- **보낸 수정 요청** (/owner/works/:id/revision/sent, `?submission=` for a past
  round, `OwnerRevisionSentPage`), laid out like 수정 요청: the work box
  (「박지은 학생, 9월 23일 수정 요청, 수정 1/2」), 「이렇게 고쳐 달라고 했어요」
  with 「추가 자료나 질문은 채팅으로 보내 주세요.」, 「요청 내용」 in an input-like
  box, 참고 사진, and for the request being worked on 「학생은 최종 마감(M월
  D일)까지 수정안을 보내요」; 「확인」 goes back. The request is the given
  submission's, or the last one sent; without one, 「보낸 수정 요청이 없어요」.
- **남긴 후기** (owner, /owner/works/:id/review/view, `OwnerReviewViewPage`):
  「박지은 학생에게 남긴 후기」, 「다른 사장님들이 학생을 고를 때 이 후기를 봐요」,
  the stars and 「5.0 · 최고예요」, the 좋았던 점 chips with the chosen ones
  filled, the text when written, and 「확인」. The student name comes from GET
  /jobs/{id}/result.
- **Student past documents** (/student/works/:id/submissions/:submissionId and
  .../request, `StudentPastSubmissionPage`): 제출한 초안 · 수정안 shows the work
  box (「공룡카페 · 9월 22일 제출 · 수정 0/2」), the flow bar, 「사장님이 이 초안에
  수정을 요청했어요」 when it got one, 원본 파일, and 내가 남긴 한마디; 받은 수정
  요청 shows the owner's request (date, text, 참고 사진) and the files it was
  about. Both have only 「확인」.
- **의뢰서 전체 보기 · 작업 중** (student, `applied` ACCEPTED): the room's
  status line, a flow bar at the current stage (작업 중 · 확인 중), the store,
  the terms, 「내 작업계획서」 from the room's application, then 할 일 ·
  맡기고 싶은 일 · 참고 자료, with 「확인」 in the footer instead of the
  selection guide. Without a room it shows the job status and no plan.
- **내 활동** (owner, 진행 중): 작업 취소 shows only while drafting, and a gray
  line sits above the 「문제가 있나요?」 row.

## Rationale

- The status line and one history link keep the card short; every document
  is two taps away and opens a screen.
- 「문제가 있나요?」 looks like the 내 활동 card and keeps 작업 취소 · 문제 신고
  out of the way until asked.
- The same names on both sides, and every document opens a screen, so the
  chat never mixes sheets and screens.
- The room's fields and the history answer the stage, the proposal, and the
  rounds, so the chat and the history need no matched or proposal lists.

## Alternatives Considered

- Document chips on the card: they repeated the status line and did not fit
  a phone.
- A 「⋯」 menu for 작업 취소 · 문제 신고: hard to notice and unlike the 내 활동
  card.
- Dates on the history rows: each screen shows its own dates, and the rows
  stay short.

## Agent Guidance

- A new kind of document gets its rows in `chatWorkEntriesFromSubmissions`
  and a path in both `ownerWorkDocPath` and `studentWorkDocPath`.
- A new card action for trouble goes in the `trouble` list, not in a new row.

# 0029. Student starts work from an accepted proposal

## Status

Accepted. A proposal the owner accepted and paid for (AWAITING_START) opens a
work-start screen that reads GET /proposals/{id} and starts the work with
POST /jobs/{jobId}/start.

## Context

- 「조건 확인하기」 (보낸 제안서 상세, 내 활동 보낸 제안) and the home 「제안이
  받아들여졌어요 · 조건 확인」 card had no screen that opens a real proposal
  after ADR 0023.
- GET /proposals/{id} has `jobId` and, for the proposal's owner and student
  after payment, `agreement` (`jobStatus`, `budget`, `draftDeadline`,
  `finalDeadline`, `revisionCount`, `messageToStudent`, `paidAt`,
  `startedAt`).
- POST /jobs/{jobId}/start takes `{ deadlineAndPenaltyAgreed: true }` and
  answers `{ jobId, jobStatus, proposalStatus, startedAt, chatRoomId,
  draftDeadline, finalDeadline }`. A repeat answers with the first start.
  Errors: 403 JOB_START_403 (not the proposal's student), 404 JOB_404,
  409 JOB_START_409 (not startable).
- POST /jobs/{jobId}/decline declines the request (「이 조건은 어려워요」,
  ADR 0033).

## Decision

- **Route** /student/proposals/:proposalId/start
  (`src/pages/StudentProposalStartPage.tsx`, `STUDENT_PATHS.proposalStart`).
  `useProposalDetail` loads the proposal. Anything but AWAITING_START with an
  `agreement`, a `jobId`, and a job that is not CANCELLED replaces the route
  with the sent-proposal detail. 404 shows `StudentMissing`; other failures
  show `LoadNotice` with 「다시 시도」.
- **Screen** (Figma 「작업 시작 - 의뢰서 확인·약관 동의」, top to bottom):
  the flow bar (제안 done, 시작 「동의해 주세요」), 「사장님이 제안을
  받아들였어요」, the 「내 차례」 notice, the store box with 「의뢰서 M월 D일
  도착」 from `paidAt` (local date), 「사장님이 보낸 의뢰서」 (작업비, 초안 ·
  최종 마감, 수정 n회), 「사장님의 한마디」 when not blank, an 8 px gray band
  across the screen (as in 탐색), 「내가 보낸 제안서」 (문제, 해결, 작업계획서,
  「50,000원 · 수락 후 초안 2일 · 최종 4일」 on one line, and the reference photos) in
  one gray box, 「시작 전에 약속해요」, and the terms check.
- **Footer**, side by side: 「이 조건은 어려워요」 (gray, left) and 「동의하고
  작업 시작하기」 (enabled after the check, 「시작하는 중...」 while sending, one
  request per press).
  - Start: `sendWorkStart` (`src/features/student/lib/workStart.ts`). Success
    opens 「작업을 시작했어요」 with the answer's draft deadline; 「확인」 goes to
    내 활동 › 진행 중. 401 → /login; JOB_START_403 → 「제안한 학생만 작업을 시작할
    수 있어요」 then `landingPath()`; JOB_START_409 or JOB_404 → 「지금은 작업을
    시작할 수 없어요…」 above the buttons; anything else → 「잠시 후 다시 시도해
    주세요」.
  - Decline: 「의뢰서를 거절할까요?」, then 「거절하기」 declines (ADR 0033).
- **Entries**: the sent-proposal detail footer and the 내 활동 보낸 제안 card
  show 「조건 확인하기」 for AWAITING_START with the job not cancelled; the home
  「확인할 일」 puts those proposals first as `proposalAgreement` cards
  (「제안이 받아들여졌어요」 · 「조건 확인」).

## Rationale

- Keying the screen by proposal id reuses the proposal the student already
  opens and carries the `jobId` the start call needs.

## Alternatives Considered

- Reusing /student/works/:id/start with the job id: rejected, the job detail
  has no link back to the proposal text the screen shows.

## Agent Guidance

- A started job shows in 내 활동 › 진행 중 (GET /me/jobs?status=MATCHED,
  ADR 0032).

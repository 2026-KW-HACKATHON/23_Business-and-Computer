# 0035. Owner in-progress work, work check, revision request, and cancel call the job APIs

## Status

Accepted. 내 활동 › 진행 중, the home 「확인할 일」 초안 · 수정안 cards and
「학생이 작업 중」 rows, 작업 확인, 수정 요청, and 작업 취소 read and write the
backend for jobs a student is working on. Sample works (ids like
`work-103`) still open the old sample screens, because sample notifications
link to them.

## Context

The backend (dev) has:

- GET /me/jobs?status=MATCHED (owner) → `{ jobs: [{ jobId, title,
  specialtyCategories, draftDeadline, finalDeadline, studentProfileId,
  studentNumber, major, submissionType, pendingSubmissionId, progressStage
  }] }`, newest first. `pendingSubmissionId` · `submissionType` describe a
  submission waiting for the owner; `progressStage` is STARTED (nothing
  submitted), DRAFT, or REVISION. It has no student name, budget, or
  revision count.
- GET /me/chat-rooms → one room per paid job with `jobId`,
  `counterpartName` (the student, for an owner), `budget`, `revisionCount`,
  and the application's summary · work plan · delivery method.
- GET /jobs/{jobId}/submission (owner) → the pending submission
  `{ submissionId, title, studentName, submissionType, fileUrls, message,
  revisionNumber }` (draft 0, revisions from 1), or JOB_SUBMISSION_404. File
  URLs end with the uploaded file name.
- POST /jobs/{jobId}/submissions/{submissionId}/revision-request with
  `{ message (required, ≤ 500), referenceImageUrls (up to 4 JOB images) }`
  and .../complete. Errors: JOB_404, JOB_SUBMISSION_404,
  JOB_SUBMISSION_409_REVIEW_STATUS (the job is not in progress),
  JOB_SUBMISSION_409_REVIEWED, JOB_SUBMISSION_409_REVISION_LIMIT
  (`revisionNumber` ≥ `revisionCount`), and JOB_400_IMAGE_URL ·
  JOB_409_IMAGE_NOT_UPLOADED for the photos.
- POST /jobs/{jobId}/cancel on a job in progress refunds the budget minus
  20% student compensation and returns `paidAmount`,
  `studentCompensationAmount`, and `refundAmount`. A job with a submitted
  result is refused (JOB_409_CANCEL_SUBMITTED).
- Submissions have no date, and nothing completes a job automatically.

## Decision

- **Data** (`src/features/owner/lib/progressJobs.ts`,
  `src/features/owner/hooks/useOwnerProgressJobs.ts`): `loadOwnerProgressJobs`
  reads the matched list (student name, budget, and revision count come
  from it since ADR 0039), and `kind` (proposal when a received proposal has
  that `jobId`; its student name is the fallback) from GET
  /me/received-proposals. That may fail; the list still shows, with 「학생」
  for a missing name. The application loads when its sheet opens (ADR
  0039). Stage: pending submission →
  submitted, REVISION → revising, otherwise drafting. The deadline shown is
  the draft deadline while drafting, the final deadline after that. 401
  goes to /login.
- **내 활동 › 진행 중**: cards sorted by that deadline, with category badges,
  the status (초안 제작 중 · 수정안 제작 중 · 초안/수정안이 도착했어요), the
  student line (name · 학번 · 학과; 「프로필 보기」 opens the student's
  profile, ADR 0038), and either 초안/수정안 확인하기 · 문의하기 (채팅
  목록) or 작업 취소 · 문제 신고. 「상세보기」 opens the work check when
  something arrived, otherwise 보낸 의뢰 for a request or the received
  proposal for a proposal (ADR 0049). The count shows 「-」 and `LoadNotice` replaces
  the list while loading or after a failure.
- **Home**: arrived submissions are 「확인할 일」 cards (「초안/수정안이
  도착했어요」, 「M월 D일까지 확인하지 않으면 자동으로 완료돼요」, ADR 0039); drafting ·
  revising jobs are 「학생이 작업 중」 rows that open 보낸 의뢰 or the
  received proposal (ADR 0049). A failed load shows one 「다시 시도」 line, and the
  확인할 일 count waits for both lists.
- **작업계획서 sheet** (`WorkPlanSheet`): takes `WorkPlanSheetContent` and
  leaves out the date, fee, and revision rows it does not know.
- **작업 확인** (`src/pages/OwnerJobCheckPage.tsx`, /owner/works/:id/check
  with a numeric id): the summary (student · 초안/수정안 도착 M월 D일 · 수정
  n/m), flow bar, 「M월 D일까지 확인해 주세요」 with the revisions left (ADR
  0039), the
  files with 「받기」 links (the name comes from the URL), and the student's
  message. 「수정 요청」 is hidden when no revision is left. 「완료 확인」
  completes (「완료하는 중...」, one request per press) and goes to 후기 작성
  (ADR 0036).
- **수정 요청** (/owner/works/:id/revision with a numeric id): the text is
  required; up to 4 reference photos (JPG · PNG · WEBP, 10MB each; others
  are dropped with a notice) are uploaded as JOB images first, then sent
  with the text. 「보내는 중...」, then the done popup → 내 활동 › 진행 중.
  Limit reached → 「남은 수정 요청이 없어요…」; already reviewed or no longer
  in progress → 「이미 확인했거나 끝난 작업이에요…」; a photo that fails →
  「참고 사진을 올리지 못했어요…」.
- **작업 취소** (/owner/works/:id/cancel with a numeric id): reason, message,
  refund breakdown, and the check box as in Figma. A job with an arrived
  submission shows 「결과물을 받은 뒤에는 취소할 수 없어요…」 (the refund
  policy). The done popup uses the refund and compensation from the response
  → 내 활동 › 완료.
- Every server screen shows 「진행 중인 작업이 아니에요」 when the job is not
  in the matched list, and the check and revision screens show 「확인할
  결과물이 아직 없어요」 when nothing is pending.

## Rationale

- Keeping sample works for non-numeric ids leaves sample notifications
  working until they read the backend.

## Alternatives Considered

- Waiting for the matched list to carry the student name: rejected, the
  screens would show 「학생」 until then.

## Agent Guidance

- The matched list carries the name, fee, revision count, and arrival time
  (ADR 0039); GET /me/chat-rooms is only for the application sheet.

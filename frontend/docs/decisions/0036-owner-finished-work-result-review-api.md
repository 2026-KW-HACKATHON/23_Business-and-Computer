# 0036. Owner finished work, result, review, and not-concluded detail call the job APIs

## Status

Accepted. 내 활동 › 완료 (완료 · 성사되지 않은 일), the home 「끝난 일」, 지난
결과물 보기, 후기 작성 · 후기 완료, and 성사되지 않은 작업 상세 read and write
the backend for the owner's finished jobs. 「완료 확인」 on 작업 확인 now goes
to 후기 작성.
The payment summary above the 완료 list and 결제 내역 read GET /payments
(ADR 0040).

## Context

The backend (dev) has:

- GET /me/jobs?status=CLOSED (owner) → `[{ jobId, title,
  specialtyCategories, matchedWorker { studentProfileId, name }, completedAt,
  progressStage, reviewed }]` (`data` is the array itself, ADR 0042), newest first. It lists completed jobs (progressStage
  COMPLETED) and cancelled or declined ones (CANCELLED); `matchedWorker` is
  null for a request cancelled while recruiting. It has no fee or refund; `reviewed`
  says whether the owner reviewed.
- GET /payments (owner) → months of `{ jobId, amount, refundAmount, status }`
  (HELD, SETTLED, PARTIALLY_REFUNDED, FULLY_REFUNDED).
- GET /jobs/{jobId}/result → `{ title, studentName, completedAt,
  normalCompleted, workFee, fileUrls, message, workHistory [{ type, date }] }`
  for a completed job of the owner or the student; JOB_RESULT_404 otherwise.
  `normalCompleted` is always true for now. File URLs end with the file name;
  `files [{ fileUrl, size }]` gives each file's size in bytes (null for a file
  submitted before sizes were stored).
- POST /jobs/{jobId}/reviews with `{ rating (1–5), positivePoints (up to 5
  distinct of QUALITY_OUTPUT, ON_TIME_DELIVERY, FAST_COMMUNICATION, KINDNESS,
  REVISION_FEEDBACK), content (optional, ≤ 5000) }`, once per job. Errors:
  REVIEW_409_DUPLICATE, REVIEW_409_STATUS, JOB_404. The owner cannot read the
  review back (GET /jobs/{jobId}/review is for the student).
- GET /jobs/{jobId} on a cancelled job gives its owner `cancelledBy` (OWNER or
  STUDENT for a declined proposal request), `cancelReason`,
  `messageToStudent`, `refundAmount`, `studentCompensationAmount`, and
  `cancelledAt`; the amounts are null when nothing was paid.

## Decision

- **Data** (`src/features/owner/lib/closedJobs.ts`,
  `src/features/owner/hooks/useOwnerClosedJobs.ts`): `loadOwnerClosedJobs`
  reads the closed list, then fills each job's paid amount and refund from GET
  /payments and `kind` from GET /me/received-proposals. Those two may fail;
  the list still shows without the amount lines. CANCELLED → 성사되지 않음,
  everything else → 완료. 401 goes to /login.
- **내 활동 › 완료**: 완료 cards (category badges, 「○○ 학생, 10월 6일 완료」,
  「작업비 ○원 정산 완료」, 결과물 보기 · 후기 남기기) and 「성사되지 않은 일」
  cards (「작업비 ○원 중 ○원 환불」 when paid, 상세보기). A request cancelled
  while recruiting shows only the date. The count shows loading dots (ADR 0059) and `LoadNotice`
  replaces the list while loading or after a failure. 「후기 작성 완료」 shows
  when the list says `reviewed`, or for a job reviewed in this session
  (`isOwnerWorkReviewed`) before the list reloads.
- **Home 끝난 일**: completed jobs from GET /me/home (ADR 0064); a failed
  section shows 「끝난 일을 불러오지 못했어요」 with 「다시 시도」.
- **지난 결과물 보기** (/owner/works/:id/result with a numeric id): GET
  /jobs/{id}/result. The meta shows the student, the completion date and how
  (직접 확인 · 7일 지나 자동 완료), and the fee. Files show their name, their
  size when known (「24.1MB」, `fileSizeOfUrl`), and a 「받기」 link (`DownloadButton` with `href`); 작업 기록 shows 안전결제 · 작업
  시작, 초안 도착, 수정 요청, 수정안 도착, and 사장님이 완료 확인 (or 7일 지나
  자동 완료).
- **후기 작성** (/owner/works/:id/review with a numeric id): the student name
  comes from the result; an unfinished job shows 「아직 끝나지 않은
  작업이에요」. 「후기 남기기」 needs a rating and sends the chips as
  `positivePoints`, and the text as `content` only when one is written.
  Success → 후기 완료
  with the rating and student name. Duplicate → 「이미 후기를 남긴 작업이에요」.
  「건너뛰기」 → home.
- **작업 확인**: 「완료 확인」 → POST .../complete → 후기 작성 (Figma flow).
- **성사되지 않은 작업 상세** (/owner/works/:id/canceled with a numeric id):
  GET /jobs/{id} for the cancel data, the closed list for the student name and
  kind. The text differs for a student decline (full refund), a cancel during
  work (20% to the student), and a cancel while recruiting (nothing paid). The
  refund breakdown shows only when something was paid; the reason is hidden
  for a student decline.

## Rationale

- One GET /payments call fills the amounts for every finished job instead
  of one GET /jobs/{id} per job.
- The review text is optional as in Figma, so an empty field sends no
  `content`.

## Alternatives Considered

- Making the review text required: rejected, the Figma field is optional.

## Agent Guidance

- A review may have no text; screens that show reviews hide the text line
  then (`content` is null).

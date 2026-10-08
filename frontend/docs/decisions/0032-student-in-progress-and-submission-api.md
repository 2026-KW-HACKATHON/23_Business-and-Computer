# 0032. Student in-progress work and draft · revision submission call the job APIs

## Status

Accepted. 내 활동 › 진행 중, the home 「확인할 일」 drafting · revising cards and
「사장님이 확인 중」 rows, 초안 제출, 수정 요청 확인, 수정안 제출, and 제출한
초안 read and write the backend for jobs matched to the student.

## Context

The backend (dev) has:

- GET /me/jobs?status=MATCHED (student) → `{ jobs: [{ jobId, title,
  specialtyCategories, budget, draftDeadline, finalDeadline, revisionCount,
  submissionType, reviewStatus, progressStage }] }`, newest first.
  `submissionType` · `reviewStatus` describe the latest submission (null
  before the first draft). It has no store name.
- GET /jobs/{jobId} → `storeName` · `storeAddress`.
- GET /me/proposals → `jobId` of accepted proposals.
- POST /jobs/{jobId}/submission/uploads with `{ type: IMAGE | FILE,
  fileName, contentType, size }` → `{ uploadUrl, uploadHeaders, fileUrl }`
  (same formats and sizes as chat attachments: jpg · jpeg · png · webp · gif
  up to 10MB, pdf · zip · doc(x) · xls(x) · ppt(x) up to 50MB).
- POST /jobs/{jobId}/submission (first draft) and
  POST /jobs/{jobId}/submission/revisions (after a revision request) with
  `{ fileUrls (1–10, distinct), message (required, ≤ 5000) }`. Errors:
  JOB_SUBMISSION_409_DUPLICATE, JOB_SUBMISSION_409_REVISION_NOT_REQUESTED,
  JOB_SUBMISSION_409_STATUS, JOB_SUBMISSION_403, JOB_404,
  CHAT_UPLOAD_400_TYPE · _SIZE, JOB_SUBMISSION_400_FILE_URL,
  JOB_SUBMISSION_409_FILE_NOT_UPLOADED.
- GET /jobs/{jobId}/submissions/latest gives the student their latest
  submission and the revision request on it (ADR 0038).

## Decision

- **Data** (`src/features/student/lib/progressJobs.ts`,
  `src/features/student/hooks/useProgressJobs.ts`): `loadProgressJobs` reads the matched list,
  then fills each job's store from GET /jobs/{id} and `kind` (proposal when a
  sent proposal has that `jobId`). Those two may fail; the list still shows,
  without the store line and as a request. Stage: no submission → drafting,
  REVISION_REQUESTED → revising, any other review status → submitted. The
  deadline shown is the draft deadline while drafting, the final deadline
  after that. 401 goes to /login.
- **내 활동 › 진행 중**: cards sorted by that deadline, with category badges,
  status (초안 제작 중 · 수정 요청이 왔어요 · 초안/수정안 제출, 사장님 확인
  중), store line when known, and 초안 제출하기 · 수정안 제출하기 · 문의하기
  (그 작업의 채팅방, ADR 0057). The count shows loading dots (ADR 0059) and `LoadNotice` replaces the list while
  loading or after a failure.
- **Home**: drafting · revising jobs join 「확인할 일」 (sorted with the
  agreement cards by deadline); submitted jobs are 「사장님이 확인 중」
  rows. A failed load shows one 「다시 시도」 line.
- **Submit** (`src/pages/StudentJobSubmitPage.tsx`, routes
  /student/works/:id/submit and /student/works/:id/revision/submit when the
  id is a number):
  files go through `FilePicker` (`accept` lists the formats of
  `src/lib/attachmentFormats.ts`, shared with chat attachments; files that do not
  fit and files past 10 are dropped with one notice), the message is required
  (「꼭 적어 주세요」), then `sendSubmission` uploads each file and submits.
  Loading dots while sending (ADR 0059), one request per press. Success opens the
  done popup → 내 활동 › 진행 중. Duplicate → alert and 제출한 초안; not
  requested or not available → alert and 내 활동 › 진행 중; upload or file
  errors → 「파일을 올리지 못했어요…」; anything else → 「잠시 후 다시 시도해
  주세요」.
- **수정 요청 확인 · 제출한 초안** (`src/pages/StudentJobStagePages.tsx`): the
  job summary, flow bar, and buttons; the revision request and the
  student's own files and message come from the latest submission (ADR 0038).
- Every server screen sends the student to the screen of the job's current
  stage when the address points at another stage.

## Rationale

- Matched jobs are few per student, so one GET /jobs/{id} each for the store
  is cheap until the list carries the store name.

## Alternatives Considered

- Waiting for the matched list to carry the store name: rejected, the
  screens would show no store until then.

## Agent Guidance

- The matched list carries the store name and submission time (ADR 0039);
  only 내 활동 still calls GET /jobs/{id}, for the store address.

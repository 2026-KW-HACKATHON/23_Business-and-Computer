# 0042. Student finished work reads the settlements and the job APIs

## Status

Accepted. 내 활동 › 완료 (완료 · 성사되지 않은 일), the home 「끝난 일」, 내
작업물 모아보기, 내 결과물, 받은 후기, and 성사되지 않은 작업 read the backend
for the student's finished jobs, and every 정산 내역 row opens its screen.
The owner side gets three fixes found while checking the demo data: 내 활동 ›
완료 reads the closed list, 내 정보 counts open requests, and 성사되지 않은
작업 shows the Korean cancel date. Updates ADR 0018, ADR 0036, ADR 0040, and
ADR 0041.

## Context

- GET /me/jobs?status=CLOSED is owner-only, so there is no student closed
  list.
- Every job a student works on is paid, so GET /settlements (ADR 0041) lists
  all of their finished jobs: SETTLED (completed), START_COMPENSATION (the
  owner cancelled during work), and REFUNDED (the student declined before
  starting). SCHEDULED rows are still in progress. Rows carry `jobId`,
  `title`, `storeName`, `amount`, and `settledDate`, but no specialties,
  review, or files.
- GET /jobs/{jobId}/result serves the owner and the matched student (ADR
  0036). GET /jobs/{jobId}/review (student) → `{ submissionId, jobTitle,
  storeName, rating, createdAt, positivePoints, content }`, REVIEW_404 when
  the owner has not reviewed. GET /jobs/{jobId} gives the matched student the
  cancel data (`cancelledBy`, `cancelReason`, `messageToStudent`,
  `refundAmount`, `studentCompensationAmount`, `cancelledAt` in UTC).
- GET /me/jobs?status=CLOSED answers with `data` as the array itself, not
  `{ jobs }`.
- `sentJobCount` in GET /owners/me counts every request that is not
  cancelled, while 내 활동 › 보낸 의뢰 lists only open requests.

## Decision

- **Finished list** (`src/features/student/lib/finishedJobs.ts`,
  `useFinishedJobs` in `src/features/student/hooks/useFinishedJobs.ts`): GET
  /settlements without the SCHEDULED rows, newest first. SETTLED → 완료, the
  rest → 성사되지 않음, and `kind` comes from the `jobId` of GET /me/proposals.
  Each screen asks per job only for what it shows: specialties (GET
  /jobs/{id}), the rating (GET /jobs/{id}/review), or files (GET
  /jobs/{id}/result). A failed extra call leaves that part out. 401 goes to
  /login.
- **내 활동 › 완료** (`src/pages/StudentActivityPage.tsx`): 완료 cards
  (category badges, 「가게, 10월 7일 완료」, 「작업비 ○원 정산 완료」, 내 결과물
  보기, and 「받은 후기 ★ 5.0」 when reviewed) and 「성사되지 않은 일」 cards
  (「착수 보상 ○원 정산 완료」 when paid out, 상세보기). While loading or after a
  failure the count shows loading dots (ADR 0059) with `LoadNotice`.
- **Home** (`src/features/student/hooks/useStudentHome.ts`): 끝난 일 lists
  the completed jobs, and 확인할 일 holds requests waiting for agreement on my
  proposals, drafts, and revisions. The first-visit guide shows only when the
  finished, in-progress, applied, and proposal lists are all loaded and empty.
- **내 작업물 모아보기** (`src/pages/StudentPortfolioPage.tsx`): completed jobs
  by month with the first file's name and type; the three stats come from the
  same list.
- **내 결과물** (`src/pages/StudentWorkResultPage.tsx`): a numeric id loads
  GET /jobs/{id}/result with the store name (GET /jobs/{id}) and the review
  (GET /jobs/{id}/review) alongside. It shows the meta line, image tiles, the
  files with 「받기」, the review box or 「내가 남긴 한마디」, and the work history
  (작업 시작 · 초안 제출 · 수정 요청 받음 · 수정안 제출 · 사장님이 완료 확인 or 7일
  지나 자동 완료). 404 → 「아직 끝나지 않은 작업이에요」.
- **받은 후기** (`src/pages/StudentReviewPage.tsx`): GET /jobs/{id}/review;
  the 좋았던 점 chips use the owner's review chip words. 404 → 「아직 받은 후기가
  없어요」.
- **성사되지 않은 작업** (`src/pages/StudentWorkCanceledPage.tsx`): GET
  /jobs/{id}. 의뢰서 거절, 사장님이 작업 중에 취소 (착수 보상 20%), and 시작 전
  취소 each get their own sentence; 취소 이유 shows for an owner cancel, and the
  breakdown for a paid job. The date is the Korean date of `cancelledAt`.
- **정산 내역** (`src/pages/StudentSettlementsPage.tsx`): 정산 완료 rows open 내
  결과물, and 착수 보상 · 성사되지 않음 rows open 성사되지 않은 작업.
- **Owner**: `fetchOwnerClosedJobs` reads the array; 내 정보 counts 보낸 의뢰
  from GET /me/jobs?status=OPEN like 내 활동; the owner 성사되지 않은 작업 shows
  the Korean date of `cancelledAt`.

## Rationale

- The settlements already hold every finished job of a student, so the 완료
  screens need no new backend list.
- A student has few finished jobs, so one call per job for what a screen shows
  stays small.

## Alternatives Considered

- Waiting for a student closed list: rejected, real accounts kept showing
  sample works on the home and 내 활동 until then.
- Showing `sentJobCount` on 내 정보: rejected, it disagrees with the 보낸 의뢰
  tab it opens.

## Agent Guidance

- When the backend has a student closed list with specialties and review
  state, read it instead of one call per job.
- When the result or the settlements say a job completed automatically, show
  「자동 완료」 on the cards and 「자동 완료 정산」 on 정산 내역.

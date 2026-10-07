# 0030. Owner sent requests, applicants, and cancel call the job APIs

## Status

Accepted. 내 활동 › 보낸 의뢰, the home 「학생 고르기」 cards and 「기다리는 중」
rows, the 내 정보 보낸 의뢰 count, 보낸 의뢰서 상세, 의뢰 취소, 지원자 목록, and
지원자 프로필 read and write the backend instead of the sample requests of
ADR 0017. 이 학생에게 맡기기 and 안전결제 are in ADR 0037.

## Context

The backend (dev) has:

- GET /me/jobs?status=OPEN → `{ jobs: [{ jobId, title, specialtyCategories,
  draftDeadline, finalDeadline, revisionCount, applicantCount,
  progressStage }] }`, the owner's open requests. It has no budget.
- GET /jobs/{jobId} (ADR 0026) for the request itself.
- GET /jobs/{jobId}/applications?sort=LATEST|RATING|COMPLETED → `{ job:
  { jobId, title, specialtyCategories, budget, draftDeadline, finalDeadline },
  applicantCount, applicants: [{ jobApplicationId, studentProfileId,
  profileImageUrl, name, studentNumber, major, averageRating,
  completedJobCount, specialtyCategories, summary, workPlan,
  deliveryMethod }] }`, only for the owner's OPEN request (409
  JOB_APPLICATION_409_LIST_STATUS otherwise).
- GET /jobs/{jobId}/applications/{jobApplicationId}/profile → `{ student:
  { studentProfileId, name, university, major, studentNumber },
  proposalCount, completedJobCount, specialtyCategories, certificates:
  [{ certificateName, acquiredYear }], portfolioUrl, penaltyCount,
  reviewCount, reviews: [{ storeName, jobTitle, content, rating,
  createdAt }] }` (409 JOB_APPLICATION_409_PROFILE_STATUS for a cancelled
  request). It has no one-line intro.
- POST /jobs/{jobId}/cancel with `{ cancelReason, messageToStudent }` (both
  required, ≤ 5000) for an open or in-progress request (409 JOB_409_CANCEL
  otherwise).
- POST /jobs/{jobId}/payments takes `{ jobApplicationId,
  refundPolicyAgreed }`, so 맡기기 needs the request id and the application id.

## Decision

- **API**: `fetchOpenJobs`, `fetchJobApplications`, `fetchApplicantProfile`,
  and `cancelJob` in `src/features/owner/api/jobApi.ts`; results and helpers
  (`parsePositiveId`, `jobCategoryNames`, `jobSpecialtyNames`,
  `applicantPlan`, `averageReviewRating`, `sendJobCancel`) in
  `src/features/owner/lib/ownerJobs.ts`.
- **Hooks** (`src/features/owner/hooks/useOwnerJobs.ts`): `useOpenJobs`,
  `useJobApplications`, and `useApplicantProfile` share one loader. A
  non-numeric id is notFound without a request; a late answer for an old id
  or retry is dropped; 401 goes to /login; 403 shows an alert and then
  `landingPath()`; 409 is `closed`.
- **내 활동 › 보낸 의뢰** (`src/pages/OwnerActivityPage.tsx`): open requests by
  draft deadline, one `CategoryBadge` per category name, 「지원자 N명」 or
  「아직 지원자가 없어요」, 「상세 보기」, 「초안 마감 : M월 D일」, and
  「지원자 보기」 when anyone applied. The count shows 「-」 and `LoadNotice`
  replaces the list while loading or after a failure.
- **Home** (`useOwnerHome`): an open request with applicants is a 「학생
  고르기」 card with the category badge only (no budget line), one without is
  a 「기다리는 중」 row. **내 정보** counts open requests, or 「-」.
- **보낸 의뢰서 상세** (`src/pages/OwnerRequestPage.tsx`, GET /jobs/{id}): the
  title, category badges, 「모집 중, 지원자 N명」 (count from the applicant
  list) or the status label, the flow bar, the terms, 「의뢰 취소」 while OPEN,
  할 일, 맡기고 싶은 일, 참고 자료, and while OPEN the 「학생을 고르면 이렇게
  진행돼요」 steps. With applicants the button reads 「지원자 N명 보기」 (the
  applicant list), otherwise 「확인」. While a student works on it the screen
  shows the stage, the student, and the application (ADR 0049).
- **의뢰 취소** (`src/pages/OwnerRequestCancelPage.tsx`): sends both texts
  trimmed; 「취소하는 중...」 while sending, one request per press; success
  opens 「의뢰를 취소했어요」; 409 or 404 → 「지금은 취소할 수 없는 의뢰예요…」,
  400 → 「입력한 내용을 다시 확인해 주세요」, anything else → 「잠시 후 다시
  시도해 주세요」. A request that is not OPEN goes back to the detail.
- **지원자 목록** (`src/pages/OwnerApplicantsPage.tsx`): the request summary,
  then each applicant with name, 「NN학번」, major, the record (「★ 4.8 · 완료
  3건」 or 「첫 작업이에요」), specialty chips, and the application as a
  collapsible work plan (한 줄 요약 · 작업계획서 · 결과물). 「프로필 보기」 opens
  /owner/requests/:requestId/applicants/:applicationId; 「이 학생에게
  맡기기」 opens /owner/requests/:requestId/assign/:applicationId. A closed
  request shows 「모집이 끝나 지원자를 볼 수 없어요」.
- **지원자 프로필** (`src/pages/OwnerApplicantProfilePage.tsx`): name, school ·
  major · 「NN학번」, trust chips (제안 N회 · 패널티 N회), 완료한 작업,
  사장님 평점 (the review average), 전공역량·특기, 자격증 (year) · 포트폴리오,
  and 사장님 후기, with 「이 학생에게 맡기기」 below. The one-line intro shows
  only when the server sends `intro`.
- 의뢰 등록 (ADR 0028) no longer adds the new request to the sample list.

## Rationale

- One loader keeps the 401 / 403 / late-answer rules the same for the
  three owner reads.
- Passing the application id in the address gives the payment screen what
  POST /jobs/{jobId}/payments needs.

## Alternatives Considered

- Fetching every request's applicants for the list counts: rejected, the
  list already carries `applicantCount`.

## Agent Guidance

- /owner/requests/:requestId/assign/:applicationId (ADR 0037) takes the job
  application id as its second id.
- 「추천순」 on the applicant list does not change the order yet; the server
  sorts by LATEST, RATING, or COMPLETED.

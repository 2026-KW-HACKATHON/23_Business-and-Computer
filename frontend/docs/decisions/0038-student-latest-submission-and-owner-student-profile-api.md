# 0038. Student latest submission and owner student profile call the backend

## Status

Accepted. 수정 요청 확인, 수정안 제출, and 제출한 초안 · 수정안 (student) show
the owner's revision request and the student's own files and message from the
backend.
「프로필 보기」 on 내 활동 › 받은 제안 · 진행 중, 받은 제안 상세, and 제안서
(다른 가게) opens the student's profile from the backend. Sample works
(non-numeric ids) still open the sample screens.

## Context

The backend (dev) has:

- GET /jobs/{jobId}/submissions/latest (the matched student) →
  `{ submissionId, submissionType, revisionNumber, fileUrls, message,
  reviewStatus, submittedAt, revisionRequest { message, referenceImageUrls,
  requestedAt } | null }`: the submission with the highest revision number,
  also after the job is completed or cancelled. Errors: JOB_404 (not the
  student's job), JOB_SUBMISSION_404_LATEST (nothing submitted),
  JOB_SUBMISSION_403_VIEW (not a student). Times are UTC without an offset;
  file URLs end with the uploaded file name.
- GET /students/{studentProfileId}/profile (any owner) → the same shape as
  the applicant profile (ADR 0030). Errors: STUDENT_PROFILE_403_OWNER,
  STUDENT_PROFILE_404.
- The owner matched list, GET /me/received-proposals, and GET
  /proposals/{id} carry the student's `studentProfileId`.

## Decision

- **Student data** (`src/features/student/lib/latestSubmission.ts`,
  `src/features/student/hooks/useLatestSubmission.ts`): each stage screen
  loads the latest submission of its job. 401 goes to /login; a 404 or any
  other failure shows `LoadNotice` with 「다시 시도」. Dates are the Korean date
  of the UTC times (`koreaDateOfUtc` in `src/lib/date.ts`).
- **수정 요청 확인** (`src/pages/StudentJobStagePages.tsx`): the owner's
  request with its date, the text (line breaks kept; 「사장님이 적은 내용이
  없어요」 when there is none), and 참고 사진 (`ReferencePhotos`, tap to open
  the original); 「내가 보낸 초안 · 수정안」 lists the files by name with 「M월
  D일 보냄」 and a 「받기」 link. 「이번이 마지막 수정이에요」 shows when
  `revisionNumber + 1` reaches the revision count.
- **수정안 제출** (`src/pages/StudentJobSubmitPage.tsx`): the owner's request
  (date and text) above the file picker, as on the sample screen.
- **제출한 초안 · 수정안**: the meta reads 「가게 · M월 D일 제출 · 수정 n/m」
  (n = `revisionNumber`), 원본 파일 has 「받기」 links, and 「내가 남긴
  한마디」 (`NoteBox`) shows the message. The automatic completion line
  reads 「M월 D일까지 답이 없으면 자동으로 완료돼요」 (ADR 0039).
- `fileNameFromUrl` (`src/lib/fileUrl.ts`) names submitted files for both
  roles; the owner's `submissionFileName` re-exports it.
- **학생 프로필** (`src/pages/OwnerStudentPage.tsx`, `useOwnerStudentProfile`
  in `src/features/owner/hooks/useOwnerJobs.ts`): /owner/students/:id with a
  numeric id loads GET /students/{id}/profile and shows the same body as
  지원자 프로필 (`src/pages/OwnerStudentProfileView.tsx`), without a button
  below. A non-numeric id or 404 → 「찾는 학생이 없어요」; 403 → alert and
  the landing page.
- **프로필 보기**: 내 활동 › 진행 중 cards (replacing the 「학생 프로필은 곧 볼
  수 있어요」 popup), 내 활동 › 받은 제안 cards, 받은 제안 상세, and 제안서
  (다른 가게) open /owner/students/:studentProfileId.

## Rationale

- The two profile APIs return the same shape, so one body keeps 학생 프로필
  and 지원자 프로필 identical.
- A numeric id selects server data, as for server jobs (ADR 0032, ADR 0035),
  so sample work links keep working.

## Alternatives Considered

- Loading every job's latest submission with the progress list: rejected,
  only the two stage screens show the files, and 내 활동 would make one more
  request per job.

## Agent Guidance

- When sample works are removed, drop the non-numeric branches in
  `src/pages/StudentRevisionPage.tsx` and `src/pages/StudentSubmittedPage.tsx`.

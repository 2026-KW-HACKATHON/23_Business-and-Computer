# 0027. Student applied jobs call GET /me/job-applications

## Status

Accepted. Replaces the sample applications of ADR 0018 for 내 활동 › 지원한
의뢰, the 「내 지원서 보기」 sheet, the home 「기다리는 중」 applications, and
the 내 정보 지원한 의뢰 count. Follows ADR 0026, which sends the application.

## Context

An application sent through POST /jobs/{jobId}/applications (ADR 0026) did
not show in any student screen. The backend (dev) has:

- GET /me/job-applications → `{ data: { jobs: [...] } }`, newest application
  first. It lists only the student's PENDING applications to OPEN jobs in the
  same demo session. Each item: `jobId`, `jobApplicationId`, `title`,
  `specialtyCategories`, `budget`, `draftDeadline`, `finalDeadline`,
  `jobStatus`, `applicationStatus`, and `appliedAt` (Korea time, no offset).
  Errors: 401 COMMON_401, 403 JOB_APPLICATION_403_LIST_STUDENT (not a
  student).
- The list has no store name, no revision count, and not the application
  the student sent (summary, workPlan, deliveryMethod). No API lets a student
  read their own application, and applications that were not selected or
  whose job closed are not listed.
- GET /jobs/{jobId} (ADR 0026) has the revision count and the store name.

## Decision

- **API**: `fetchMyJobApplications` in
  `src/features/student/api/applicationApi.ts`. `loadAppliedJobs`,
  `appliedStatusLabel`, `appliedPlan`, and `appliedOnText` in
  `src/features/student/lib/appliedJobs.ts`.
- **Hook**: `useAppliedJobs` (`src/features/student/hooks/useAppliedJobs.ts`)
  works like `useSentProposals` (ADR 0023): `reload` after a failure, 401 goes
  to /login, 403 JOB_APPLICATION_403_LIST_STUDENT shows 「학생만 지원한 의뢰를 볼
  수 있어요」 and then `landingPath()`, and an answer after leaving the screen is
  dropped.
- **내 활동 › 지원한 의뢰** (`src/pages/StudentActivityPage.tsx`): one card
  per application with the title, one `CategoryBadge` per distinct category
  name, the status (`appliedStatusLabel`: PENDING 「사장님 검토 중」, ACCEPTED
  「선택됐어요」, REJECTED 「선택되지 않았어요」) after the store name when it is
  sent, 「초안 마감 : M월 D일」, and for PENDING 「의뢰서 보기」 (→ 의뢰서 전체
  보기) and 「내 지원서 보기」; otherwise 「지원 결과 보기」. While the list
  loads or after a failure the count shows loading dots (ADR 0059) and `LoadNotice` replaces the
  list, with 「다시 시도」.
- **Sheet** (`src/features/student/components/ApplicationSheet.tsx`): the
  title 「내 지원서」 (「지원 결과」 for REJECTED), 「제목, M월 D일 지원할 때
  보냄」, then 작업비 · 초안 마감 · 최종 마감 from the list and 수정 n회 from
  GET /jobs/{id} (`useJobDetail`), hidden until that answer arrives. The sent
  application (한 줄 요약 · 작업계획서 · 결과물) shows only when the list sends
  all three fields.
- **Home 「기다리는 중」**: PENDING applications after the PENDING proposals,
  「가게 의뢰에 지원」 when the store name is sent and 「의뢰에 지원」 otherwise,
  with the draft deadline; a row opens 의뢰서 전체 보기. The rows and the
  first-visit value come from GET /me/home (ADR 0065).
- **내 정보**: the 지원한 의뢰 count, or loading dots while loading or after a
  failure (ADR 0059).
- **Optional fields**: `storeName`, `summary`, `workPlan`, and
  `deliveryMethod` are optional on `AppliedJobResponse` and shown only when
  the server sends them.

## Rationale

- The same loading pattern as sent proposals keeps the two 내 활동 lists
  alike.
- One GET /jobs/{id} when the sheet opens gives the revision count without a
  request per card.
- Showing the sent application only from the server avoids showing text the
  student never sent.

## Alternatives Considered

- GET /jobs/{id} for every card to get the store name: rejected, one request
  per card for a line the list can carry.
- Keeping the application text from the apply screen in the browser:
  rejected, it is lost after a reload or on another device.

## Agent Guidance

- The list now carries `storeName`, the sent application, and REJECTED
  items (ADR 0039); read the date of `appliedAt` with `koreaDate`.

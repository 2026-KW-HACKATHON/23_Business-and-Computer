# 0018. Student screens with sample data

## Status

Accepted.

## Context

Every screen on the Figma page 「3. 학생 화면」 had to become a route before the
backend has student APIs. The demo lets viewers switch between the owner and
the student, so facts both sides show (the same work, store, or proposal) must
agree. The Figma student sample also disagreed with the owner sample in a few
places (a proposal to the owner's own store that the owner never received, a
canceled work credited to another student, an applicant missing from the
owner's applicant list).

## Decision

- Student screens live in `src/features/student` and the `Student*Page`
  files, under /student (see Notion 「페이지 주소 정리」). Screens read data
  only through the hooks in `src/features/student/hooks/useStudentData.ts` and
  `useStudentHome.ts`.
- Sample data has one source per kind: works and chat threads
  (`src/features/student/lib/sampleWorks.ts`), requests and applications
  (`sampleRequests.ts`), my and other students' proposals
  (`sampleProposals.ts`), stores, the profile, and notifications. The home,
  내 활동 counts, settlements, the portfolio, chat rows, and the profile's
  completed count, rating, and reviews are derived from them.
- Facts shared with the owner sample keep the same ids and values: work-103
  and work-090 (치킨플러스), the requests req-102 · 104 · 105 and the other
  stores' req-502 · 504 · 506 · 507, and the proposals prop-501 · 503 · 505.
  김광운's owner-side profile matches the student's own profile. Where the
  Figma student sample conflicted with the owner sample, the student sample
  changed (store or title) and Figma was updated to match.
- Demo state (agree to a request, submit a draft or revision, empathy, cancel
  a proposal, send a proposal, apply, decline a request, edit the profile and
  photo) lives in
  `src/features/student/hooks/studentStore.ts` and re-renders readers through
  `useSyncExternalStore`. A reload resets it.
- 프로필 편집 (`src/pages/StudentProfileEditPage.tsx`) opens from every 「수정」 on the
  profile screen; the section 「수정」 buttons start at their section. Name,
  school, and department are verified and stay read-only. The 내 정보 fields
  line (「디자인 / 홍보」) is derived from the chosen badges, not stored.
- The student home shows 「학생 홈 - 처음」 when the account has no works,
  applications, or proposals, derived from the lists (no backend flag).
- Shared pieces moved out of the owner feature so both roles use them:
  `src/components/FormFields/FormFields.tsx` (title, text area, budget, due
  dates, revision stepper),
  `src/components/ReportSheet/ReportSheet.tsx` with `tone` (who reports whom,
  mail text in `src/lib/support.ts`), and `src/lib/sampleTime.ts`.
- An application holds 한 줄 요약 · 작업계획서 · 결과물 (`src/types/workPlan.ts`).
  The owner already set the deadlines in the request, so the student does not
  write a schedule. Owner screens (applicants, assign, the chat 작업계획서
  sheet) show the same three parts. A work started from a proposal keeps the
  proposal's plan text instead.
- My own waiting proposals are public, so they also appear in explore marked
  「내 제안이에요」 with the empathy count only; they open my sent proposal and
  never appear under 「다른 학생들의 제안 공감하기」. A student cannot empathize
  with their own proposal.
- Explore cards show the store name only, as on the owner side. Request
  details, the store list, and my own proposals keep the store address, since
  a student visits the store.
- Popups added in code and Figma: 내 지원서 보기 · 지원 결과 보기, 의뢰서 거절
  확인, 사장님 문제 신고 - 메일 문의 안내.

## Rationale

- One source per kind keeps lists and details in step, and the hooks stay the
  only place to swap in the API.
- Matching the owner sample keeps the role switch in the demo believable.

## Alternatives Considered

- One shared sample module for both roles: cleaner, but it would rewrite the
  owner sample again; the overlap is small and listed above.

## Agent Guidance

- When an owner sample fact that the student side also shows changes, change
  it in both features (ids above) and in Figma and Notion.
- Login and signup land students on /student (`landingPath` in
  `src/features/auth/lib/session.ts`, ADR 0015).
- Uploads, portfolio export, and real reporting are frontend-only until the
  backend has student APIs.
- The new-proposal flow (stores, tasks, photos, send) and 가게 탐색 now call
  the backend (ADR 0021); its sample stores and the demo `sendProposal` are no
  longer used there. Other student screens still read the sample data.

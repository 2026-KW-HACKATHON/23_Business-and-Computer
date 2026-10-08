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
  only through the hooks in `src/features/student/hooks`.
- The home 「이런 제안은 어때요?」 examples live in
  `src/features/student/lib/sampleProposals.ts`. Everything else reads the
  backend: the new-proposal flow and 가게 탐색 (ADR 0020), sent proposals
  (ADR 0023), explore (ADR 0026), applications (ADR 0027), the work screens
  (ADR 0029, ADR 0032, ADR 0038), 내 정보, the profile, and 정산 내역
  (ADR 0041), the finished work lists and screens (ADR 0042), and 알림
  (ADR 0052).
- 프로필 편집 (`src/pages/StudentProfileEditPage.tsx`) opens from every 「수정」 on the
  profile screen; the section 「수정」 buttons start at their section. Name,
  school, and student number are verified and stay read-only. The 내 정보
  fields line (「디자인 / 홍보」) lists the categories of the chosen specialties.
- The student home shows 「학생 홈 - 처음」 when the account has no works,
  applications, or proposals. The `firstVisit` of GET /me/home now decides
  it (ADR 0065).
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
- Explore cards show the store name only, as on the owner side. The store
  list and my own proposals keep the store address, since a student visits
  the store; request details show it when the server sends it (ADR 0026).
- Popups added in code and Figma: 내 지원서 보기 · 지원 결과 보기, 의뢰서 거절
  확인, 사장님 문제 신고 - 메일 문의 안내.

## Rationale

- Reading only through the hooks keeps a screen's code the same when its
  data moves to the backend.

## Alternatives Considered

- Calling the API inside each page: each page would repeat the loading and
  error states the hooks share.

## Agent Guidance

- Login and signup land students on /student (`landingPath` in
  `src/features/auth/lib/session.ts`, ADR 0015).
- Uploads, portfolio export, and real reporting are frontend-only until the
  backend has student APIs.

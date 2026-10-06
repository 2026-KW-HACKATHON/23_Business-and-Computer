# 0026. Student 탐색 calls GET /explore and the job APIs

## Status

Accepted. The student 탐색 list, 의뢰서 전체 보기, 지원하기, the
peer-proposal detail, and the home 「다른 학생들의 제안 공감하기」 now read and
write the backend instead of the sample data of ADR 0018.

## Context

The student 탐색 tab (/student/explore), 의뢰서 전체 보기
(/student/requests/:requestId/full), and 지원하기
(/student/requests/:requestId/apply) read sample requests and proposals.
The backend (dev) has:

- GET /explore?type&specialtyCategoryId&sort&size&cursor for any active
  user (owner or student) in the same demo session.
  - type ALL (default), PROPOSAL, or JOB; cancelled jobs never appear.
  - sort LATEST (default), OLDEST, or LIKES. LIKES is only allowed with
    type PROPOSAL; otherwise 400 COMMON_400.
  - size 1–100 (default 20). The cursor is opaque and only valid for the
    same type, sort, and category; a mismatch is 400 COMMON_400.
  - Answer: { items, nextCursor, hasNext }. Each item has type
    PROPOSAL (proposalId, title, storeName, studentName, status,
    proposedSolution, likeCount, likedByMe, specialtyCategories) or JOB
    (jobId, storeName, title, progressStage, status, draftDeadline,
    finalDeadline, budget, specialtyCategories, applied).
    specialtyCategories is [{ id, name, specialties: [{ id, name }] }].
  - `applied` is the student's own application status (PENDING, ACCEPTED,
    or REJECTED). It is left out when they have not applied, and for owners.
- GET /jobs/{jobId} for any active user in the same demo session; another
  session's job is 404 JOB_404. Answer: id, title, description,
  referenceImageUrls, budget, specialtyCategories, draftDeadline,
  finalDeadline, revisionCount, progressStage, status, storeName,
  storeAddress (null without an address), and `applied` as on the card. The
  cancel fields come only for a cancelled job's owner and selected student.
- POST /jobs/{jobId}/applications with { summary (≤255), workPlan (≤500),
  deliveryMethod (≤500), deadlineAndPenaltyAgreed: true } → 201
  { jobApplicationId }. Errors: 403 JOB_APPLICATION_403_STUDENT (not a
  student), 404 JOB_404, 409 JOB_APPLICATION_409_STATUS (not OPEN),
  409 JOB_APPLICATION_409_DUPLICATE (already applied), 400 for invalid input.
- GET /proposals/{proposalId} (ADR 0023) answers any active user in the
  same demo session, so it also serves other students' proposals. Besides the
  fields in ADR 0023 it has `student` (`studentProfileId`, `name`, `major`,
  `studentNumber` as the two-digit entry year, `averageRating` (0 with no
  reviews), `completedJobCount`) and `likedByMe` (false for anyone who is not
  a student). `agreement` comes only for the owner and the student of that
  proposal, so another student never sees it.
- POST and DELETE /proposals/{proposalId}/likes turn a student's like on and
  off (a repeat also succeeds) and answer { proposalId, likeCount,
  likedByMe }.
- GET /me/job-applications lists the student's pending applications to open
  jobs.
- There is no title or store search.

## Decision

- **New feature** `src/features/explore` (public entry
  `src/features/explore/index.ts`):
  - `fetchExplore` in `src/features/explore/api/exploreApi.ts` uses
    `apiData` with size 20 unless the query gives `size`.
  - `useExplore` (`src/features/explore/hooks/useExplore.ts`) keeps the
    loaded pages, `hasNext`, a separate state for the next page (`more`:
    idle / loading / error), `loadMore`, and `reload`. A new filter starts
    again from the first page; responses for an old filter or retry are
    dropped. 401 goes to /login.
  - `useExploreFeed` turns the screen's tab, field, and sort into the query.
- **Field filter**: the six Figma fields map to a server category id by
  name through `useSpecialties` (no hardcoded ids). While the specialty
  list loads the list shows loading; if it fails, 「다시 시도」 reloads the
  specialty list; a field with no category of that name shows the empty
  text without a request.
- **Sort**: the 「최신순」 text button shows the current sort and opens
  `ExploreSortSheet` (최신순 · 오래된순 · 공감 많은 순). 공감 많은 순 is offered
  only on the 제안 tab; switching to another tab sets the sort back to
  최신순, and the query never sends LIKES with another type.
- **Search**: client side, over the cards loaded so far (title or store
  name). The list keeps loading pages as the end comes into view, so a
  search reaches later pages too.
- **Infinite loading**: `useLoadMoreSentinel` watches an empty element after
  the list (IntersectionObserver, 200 px early). `LoadNotice` shows the first
  page's loading / failure with 「다시 시도」, and the next page's loading /
  failure under the list (its 「다시 시도」 asks for the same cursor again).
  「조건에 맞는 의뢰·제안이 없어요」 shows only when nothing matches and there is
  no next page.
- **Cards** (`src/features/student/components/RequestCard.tsx`,
  `src/features/student/components/PeerProposalCard.tsx`):
  - One `CategoryBadge` per distinct category name, then the store name.
  - Job: draft-deadline badge and 「지원하기」 while OPEN; status text
    OPEN 「모집 중」, AWAITING_START / MATCHED 「진행 중」, CLOSED 「완료」;
    「의뢰서 전체 보기 ›」 always in the footer row. Links use the job id.
  - Proposal: the empathy count only. 「내 제안이에요」 when the id is
    in `useSentProposals` (GET /me/proposals); mine opens the sent-proposal
    detail, others the peer-proposal route.
- **Optional fields**: the card types keep the newer fields optional, and
  the screen shows each only when the server sends it, never a guess:
  - job `budget` → the 「예산」 line; job `applied` (any status) →
    「지원했어요」;
  - proposal `studentName` → 「○○ 학생 → 가게」; `likedByMe` → a filled
    heart; `status` → the status line; `proposedSolution` → a two-line
    excerpt.
- The description under the title reads 「가게 의뢰에 지원하고, 다른 학생의
  제안을 손님 눈으로 둘러보세요.」
- **Job detail** (`src/pages/StudentRequestFullPage.tsx`): `useJobDetail`
  (`src/features/explore/hooks/useJobDetail.ts`) calls `fetchJobDetail`
  (`src/features/explore/api/jobApi.ts`). A non-numeric id shows
  `StudentMissing` without a request; 404 shows `StudentMissing`; 401 goes to
  /login; other failures show `LoadNotice` with 「다시 시도」. The screen
  shows the title, one `CategoryBadge` per distinct category name, the
  status text of the cards, the conditions (작업비 · 초안 마감 · 최종 마감 ·
  수정 n회), 할 일 chips (specialty names), 맡기고 싶은 일 (description),
  and the 「선택되면 이렇게 진행돼요」 steps. The footer shows only while
  OPEN: 「지원하기」, or a disabled 「지원했어요」.
- **Apply** (`src/pages/StudentApplyPage.tsx`): the same hook loads the job
  for the summary box; a job that is not OPEN shows 「지원할 수 없는
  의뢰예요」. `sendJobApplication` (`src/features/explore/lib/jobDetail.ts`)
  trims the three fields and sends 한 줄 요약 → summary, 작업계획서 →
  workPlan, 결과물 → deliveryMethod, with deadlineAndPenaltyAgreed true.
  The inputs stop at 255 / 500 / 500 characters. A double tap sends once
  (in-flight ref), a response after leaving the screen is dropped, and the
  button reads 「보내는 중...」 while sending. Success opens the 「지원서를
  보냈어요」 popup. Errors:
  - 409 JOB_APPLICATION_409_DUPLICATE → 「이미 지원한 의뢰예요」;
    409 JOB_APPLICATION_409_STATUS → 「모집이 끝난 의뢰예요」. Both keep the
    button disabled.
  - 403 JOB_APPLICATION_403_STUDENT → an alert, then `landingPath()`.
  - 404 → `StudentMissing`; 401 → /login.
  - 400 → 「입력한 내용을 다시 확인해 주세요」; anything else → 「잠시 후 다시
    시도해 주세요」.
- **Job detail slots**: `JobDetail` carries optional `storeName` (the store
  box), `storeAddress` (its address line), and `applied` (any status:
  「지원했어요」 on the detail, 「이미 지원한 의뢰예요」 with a disabled button
  on apply). Each shows only when the server sends it.

- **Peer-proposal detail** (`src/pages/StudentPeerProposalPage.tsx`,
  /student/explore/proposals/:proposalId): the shared `useProposalDetail`
  (`src/features/proposal`, ADR 0025) as the sent-proposal detail, plus
  `useSentProposals` for 「mine」.
  - A proposal in my sent list replaces the route with the sent-proposal
    detail, as the explore card does. While my sent list loads the screen
    shows the loading line; if that list fails the screen shows the proposal
    as another student's.
  - A non-numeric id or 404 shows `StudentMissing`; 401 goes to /login; other
    failures show `LoadNotice` with 「다시 시도」.
  - The screen shows the notice 「다른 학생의 제안이에요. 가게 손님의 눈으로
    읽어 보세요.」, the title, the status chip and flow bar of ADR 0023 (from
    `status` only), one `CategoryBadge` per distinct category name, 「M월 D일
    보냄」, 「학생 손님 N명이 공감했어요」, the student (name, major and
    「NN학번」, then `peerRecord`: 「★ 4.8 · 완료 3건」, 「완료 3건」 with no
    reviews, 「첫 작업이에요」 with none completed), the store box (name and
    address), 손님 눈으로 본 문제, 이렇게 바꿔 드릴게요, 작업계획서, 희망
    작업비 · 예상 기간, and reference photos (`ReferencePhotos`) when there
    are any. The footer is 「확인」.
  - Empathy is a count only. The heart is filled when `likedByMe` on
    `ProposalDetailResponse` is true.
- **Home 「다른 학생들의 제안 공감하기」**: `usePopularProposals(5)`
  (`src/features/explore/hooks/useExplore.ts`) calls GET
  /explore?type=PROPOSAL&sort=LIKES&size=5 once (no next page). `useStudentHome`
  drops my proposals (ids from `useSentProposals`) and keeps the first two.
  - The section shows only when both lists have loaded and something is left.
    While either loads, or after a failure, the section is hidden and the rest
    of the home shows as before. 401 goes to /login.
  - `PeerProposalRow` shows the title, 「○○ 학생 → 가게」 when `studentName`
    is sent (otherwise the store name), and the empathy count (filled heart
    only with `likedByMe`). A row opens the peer-proposal detail; 「전체 ›」
    opens 탐색.
  - The section does not count as the student's own activity, so the
    「학생 홈 - 처음」 check (ADR 0023) is unchanged; the first-visit home
    shows it under the guide and the examples.
- `EmpathyCount` is display-only (count and heart).

## Rationale

- One hook for the list keeps paging, late-response handling, and the 401
  redirect in one place for both roles' 탐색.
- Mapping fields by name follows ADR 0020 and ADR 0021: the server owns the
  ids.
- Showing optional fields only when present avoids fake budgets or
  「지원했어요」 that the server never said.
- Until the like API is called, a count-only heart is honest; a button that
  changes nothing on the server would not be.
- Hiding the home section on loading or failure keeps a secondary list from
  holding up the student's own work.
- Asking for five and keeping two leaves room for my own proposals near the
  top without a second request.

## Alternatives Considered

- Loading every page up front for a complete search (like 가게 탐색, ADR
  0020): rejected, the 탐색 list grows with every request and proposal.
- Hardcoding category ids: rejected, see ADR 0021.

## Agent Guidance

- The owner 탐색 screens are wired to the API in a later step; extend this
  ADR then.
- The like API, the job's `referenceImageUrls`, and GET
  /me/job-applications are wired in a later step.
- With the like API, the peer proposal detail gets the Figma 「공감하기」
  button (no count on it; the count stays in 「학생 손님 N명이 공감했어요」), the
  explore and home hearts toggle, and the explore copy goes back to Figma
  (「…하트를 눌러 손님으로서 공감해 보세요」).

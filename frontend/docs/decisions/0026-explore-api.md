# 0026. Student and owner 탐색 call GET /explore and the job APIs

## Status

Accepted. The student 탐색 list, 의뢰서 전체 보기, 지원하기, the
peer-proposal detail, the home 「다른 학생들의 제안 공감하기」, and 공감 (like)
now read and write the backend instead of the sample data of ADR 0018. The
owner 탐색 list and its read-only 제안서 · 의뢰서 now read the backend instead
of the sample data of ADR 0014.

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
  - Proposal: the heart pill (`EmpathyCount` with `onToggle`) likes the
    proposal, and a second tap takes the like back. The footer row shows
    「하트를 눌러 공감」 or 「공감했어요」. 「내 제안이에요」 when the id is in
    `useSentProposals` (GET /me/proposals); mine has a heart that cannot be
    pressed. Mine opens the sent-proposal detail, others the peer-proposal
    route.
- **Optional fields**: the card types keep the newer fields optional, and
  the screen shows each only when the server sends it, never a guess:
  - job `budget` → the 「예산」 line; job `applied` (any status) →
    「지원했어요」;
  - proposal `studentName` → 「○○ 학생 → 가게」; `likedByMe` → a filled
    heart; `status` → the status line; `proposedSolution` → a two-line
    excerpt.
- The description under the title reads 「가게 의뢰에 지원하고, 다른 학생의
  제안에 하트를 눌러 손님으로서 공감해 보세요.」
- **Likes** (`useProposalLikes` in
  `src/features/proposal/hooks/useProposalLikes.ts`, `sendProposalLike` in
  `src/features/proposal/api/proposalLikeApi.ts`): a tap shows the new count
  and heart at once and sends POST (like) or DELETE (take back)
  /proposals/{id}/likes; the answer's `likeCount` and `likedByMe` replace the
  shown values. A second tap while that proposal's request is in flight is
  ignored. A failure puts back the values from before the tap and sets
  `failedId`; 401 goes to /login; an answer after leaving the screen is
  dropped. The values stay on that screen only; the next visit reads the
  server again.
- **Job detail** (`src/pages/StudentRequestFullPage.tsx`): `useJobDetail`
  (`src/features/explore/hooks/useJobDetail.ts`) calls `fetchJobDetail`
  (`src/features/explore/api/jobApi.ts`). A non-numeric id shows
  `StudentMissing` without a request; 404 shows `StudentMissing`; 401 goes to
  /login; other failures show `LoadNotice` with 「다시 시도」. The screen
  shows the title, one `CategoryBadge` per distinct category name, the
  status text of the cards (「다른 학생이 선택됐어요」 when the job is no
  longer OPEN and `applied` is REJECTED), the conditions (작업비 · 초안 마감 · 최종 마감 ·
  수정 n회), 할 일 chips (specialty names), 맡기고 싶은 일 (description),
  「참고 자료」 (`ReferencePhotos`) when `referenceImageUrls` has any, and
  the 「선택되면 이렇게 진행돼요」 steps. The footer shows only while
  OPEN: 「지원하기」, or a disabled 「지원했어요」.
- **Apply** (`src/pages/StudentApplyPage.tsx`): the same hook loads the job
  for the summary box; a job that is not OPEN shows 「지원할 수 없는
  의뢰예요」. `sendJobApplication` (`src/features/explore/lib/jobDetail.ts`)
  trims the three fields and sends 한 줄 요약 → summary, 작업계획서 →
  workPlan, 결과물 → deliveryMethod, with deadlineAndPenaltyAgreed true.
  The inputs stop at 255 / 500 / 500 characters. A double tap sends once
  (in-flight ref), a response after leaving the screen is dropped, and the
  button shows loading dots (ADR 0059) while sending. Success opens the 「지원서를
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
  - The screen shows the notice 「다른 학생의 제안이에요. 손님으로서 공감되면
    눌러 주세요.」, the title, the status chip and flow bar of ADR 0023 (from
    `status` only), one `CategoryBadge` per distinct category name, 「M월 D일
    보냄」, 「학생 손님 N명이 공감했어요」, the student (name, major and
    「NN학번」, then `peerRecord`: 「★ 4.8 · 완료 3건」, 「완료 3건」 with no
    reviews, 「첫 작업이에요」 with none completed), the store box (name and
    address), 손님 눈으로 본 문제, 이렇게 바꿔 드릴게요, 작업계획서, 희망
    작업비 · 예상 기간, and reference photos (`ReferencePhotos`) when there
    are any, then 「공감은 다시 누르면 취소돼요. 내 제안에는 누를 수 없어요.」
    (after a failed tap: 「공감하지 못했어요. 잠시 후 다시 눌러 주세요.」).
  - The footer is 「공감하기」 (student color) before a like and the gray
    「공감했어요」 after; pressing 「공감했어요」 takes the like back. The heart
    and 「학생 손님 N명이 공감했어요」 follow the same values.
  - Only PENDING proposals take likes. AWAITING_START or ACCEPTED shows a
    disabled 「수락된 제안이에요」, REJECTED a disabled 「끝난 제안이에요」, and
    the like note is hidden. Explore cards and home rows do not toggle the
    heart for them either (a card with no `status` still can).
- **Home 「다른 학생들의 제안 공감하기」**: the `peerProposals` of GET
  /me/home (ADR 0065). The server takes the top 5 of the likes order (the
  same as GET /explore?type=PROPOSAL&sort=LIKES), drops my proposals, and
  sends the first two.
  - The section shows only when that list has loaded and is not empty.
    While the home loads, or after the section fails, it is hidden and the
    rest of the home shows as before. 401 goes to /login.
  - `PeerProposalRow` shows the title, 「○○ 학생 → 가게」 when `studentName`
    is sent (otherwise the store name), and the heart pill, which likes and
    takes back like the explore card. A row opens the peer-proposal detail;
    「전체 ›」 opens 탐색.
  - The section does not count as the student's own activity, so the
    「학생 홈 - 처음」 check (ADR 0023) is unchanged; the first-visit home
    shows it under the guide and the examples.
- `EmpathyCount` shows the count and heart; with `onToggle` it is a button
  (`aria-pressed` follows the like).
- **Owner 탐색** (`src/pages/OwnerExplorePage.tsx`, /owner/explore): the same
  `useExploreFeed`, field filter, `ExploreSortSheet`, search,
  `useLoadMoreSentinel`, and `LoadNotice` as the student list, with the
  owner's description and empty text.
  - `ExploreCard` takes `fields` (one `CategoryBadge` per distinct category
    name), the store name, and a status line. Proposal: the like count (a
    filled heart that cannot be pressed) and, when the card sends `status`,
    `receivedProposalStatusLabel` (결정 대기 · 결제 완료 · 작업 중 · 성사되지 않음).
    Job: 「모집 중」 / 「진행 중」 / 「완료」 and, while OPEN, the 「초안 M월
    D일까지」 badge.
  - Proposal cards open /explore/proposals/:id, job cards
    /explore/requests/:id. A proposal in my received list
    (`useReceivedProposals`, GET /me/received-proposals, ADR 0025) shows
    「우리 가게가 받은 제안이에요」 under the card and opens the received-proposal
    detail (/owner/proposals/:id) instead; /explore/proposals/:id for such a
    proposal replaces the route with it. While that list loads the detail
    shows the loading line; if it fails the proposal shows as another
    store's.
- **Owner read-only details**:
  - 제안서 보기 (`src/pages/OwnerExploreProposalPage.tsx`): `useProposalDetail`.
    The notice 「○○가 받은 제안이에요. 읽기만 할 수 있어요.」, the title, one
    `CategoryBadge` per distinct category name, 「가게 · M월 D일 · 상태」, the
    like count, the student (name, 「NN학번 · 학과」, record) with no profile
    link (there is no student profile API, as in ADR 0025), 손님 눈으로 본
    문제, 이렇게 바꿔 드릴게요, and 참고 사진 (`ReferencePhotos`) when there
    are any. The fee and days stay hidden.
  - 의뢰서 보기 (`src/pages/OwnerExploreRequestPage.tsx`): `useJobDetail`. The
    notice, the title, the category badges, the store, 할 일 chips, 맡기고
    싶은 일, and 참고 자료 when there are any. The budget and deadlines stay
    hidden.
  - Both: a non-numeric id or 404 shows `OwnerMissing`; 401 goes to /login;
    other failures show `LoadNotice` with 「다시 시도」. 「우리 가게에도 비슷한
    의뢰 만들기」 opens 의뢰 등록 with the detail's categories picked (and,
    for a job, its tasks); categories and tasks not on GET /specialties are
    left out (ADR 0058).

## Rationale

- One hook for the list keeps paging, late-response handling, and the 401
  redirect in one place for both roles' 탐색.
- Mapping fields by name follows ADR 0020 and ADR 0021: the server owns the
  ids.
- Showing optional fields only when present avoids fake budgets or
  「지원했어요」 that the server never said.
- Showing the tap at once keeps the heart responsive; putting the old
  values back on failure keeps the screen from claiming a like the server
  never stored.
- Own proposals cannot be liked, as the Figma note says, even though the
  server would accept it.
- Hiding the home section on loading or failure keeps a secondary list from
  holding up the student's own work.
- Asking for five and keeping two leaves room for my own proposals near the
  top without a second request.

## Alternatives Considered

- Loading every page up front for a complete search (like 가게 탐색, ADR
  0020): rejected, the 탐색 list grows with every request and proposal.
- Hardcoding category ids: rejected, see ADR 0021.

## Agent Guidance

- Sent applications are listed in 내 활동 through GET /me/job-applications
  (ADR 0027).

# 0025. Owner received proposals call the backend API

## Status

Accepted. Replaces the sample `OwnerProposal` data (now deleted) for 내 활동 ›
받은 제안, the received-proposal detail, the proposal summary on the accept
screen, the 「새 제안」 cards in the owner home's 확인할 일, and the 내 정보
받은 제안 count (ADR 0012, 0017). Moves the proposal detail code that both
roles use out of the student feature (ADR 0023).

## Context

The owner side still read `SAMPLE_PROPOSALS`, so a proposal a student sent
(ADR 0020) never reached the owner. The backend (dev) has:

- `GET /me/received-proposals` → `{ data: { proposals: [...] } }`, newest
  first, every status, no paging. Each item has these fields:
  - `proposalId`, `title`, `status`, `likeCount`, `specialtyCategories`,
    `proposedSolution`;
  - `student` (`studentProfileId`, `name`, `studentNumber`, `major`). Here
    `studentNumber` is the full 10 digits;
  - `jobId`, which is null before payment.
  - There is no `createdAt` and no `jobStatus`. Both have been requested
    from the backend.
  - Errors: 401 `COMMON_401`, 403 `PROPOSAL_403_LIST_OWNER` (not an owner),
    403 `OWNER_403` (no owner profile).
- `GET /proposals/{id}` is the same endpoint the student uses (ADR 0023). For
  the owner the relevant fields are:
  - `student` (`studentProfileId`, `name`, `major`, `studentNumber` as the
    two-digit admission year such as "24", `averageRating` or null,
    `completedJobCount`);
  - `createdAt` (Korea time, no offset);
  - `agreement`, after payment, only for the owner who received the proposal
    and the student who sent it.
  - Any active user in the same demo session can open any proposal. Another
    store's proposal comes without `agreement`, and nothing in the response
    says whose store it is. A different session gives 404 `PROPOSAL_404`.
- There is no API to reject a proposal or to read the proposing student's
  profile. `POST /proposals/{id}/payments` exists; paying from the accept
  screen is wired in a later issue.

## Decision

- **Shared proposal feature** `src/features/proposal`, following the
  `src/features/specialty` pattern (ADR 0020):
  - Holds `fetchProposalDetail` with its response types (`ProposalStatus`,
    `ProposalJobStatus`, `ProposalSpecialtyCategory`,
    `ProposalAgreementResponse`, `ProposalStudentResponse`,
    `ProposalDetailResponse`).
  - Holds `loadProposalDetail`, `parseProposalId`, and `useProposalDetail`
    (loading / error / notFound / loaded; 401 → /login).
  - Holds `proposalBadgeNames`, `estimatedDeadlineText`, and
    `proposalMonthDay`, which turns the text of `createdAt` into 「M월 D일」.
  - The student feature keeps only the student wording (`sentOnText`, status
    chip, flow bar) and `useSentProposals`. Student behavior is unchanged.
- **Owner code** in `src/features/owner`:
  - `src/features/owner/api/receivedProposalApi.ts` (`fetchReceivedProposals`,
    using `apiData`).
  - `src/features/owner/lib/receivedProposals.ts`: result values and display
    helpers.
  - `src/features/owner/hooks/useReceivedProposals.ts`: loading / error /
    loaded with
    `reload`. It drops late responses. 401 goes to /login.
    `PROPOSAL_403_LIST_OWNER` or `OWNER_403` shows the alert 「사장님만 받은
    제안을 볼 수 있어요」, then goes to `landingPath()`.
- **One hook for three places**: 내 활동 › 받은 제안, the home 「확인할 일」,
  and the 내 정보 count all use `useReceivedProposals`.
  - The summary counts read 「-」 while loading or after a failure.
  - 내 활동 shows `LoadNotice` with 「다시 시도」.
  - Home: only PENDING proposals become 「새 제안」 cards. The card badge is
    the first category name, and the student line shows `major`. While the
    list loads or after a failure, 확인할 일 shows `LoadNotice` and no count.
  - `firstVisit` is still the sample flag.
- **Status chip** (`receivedProposalStatusLabel`):
  - PENDING 「결정 대기」, AWAITING_START 「결제 완료」, ACCEPTED 「작업 중」,
    REJECTED 「거절됨」.
  - A CANCELLED job status overrides them with 「취소됨」. On the detail that
    comes from `agreement.jobStatus`; on list cards it shows once the
    backend adds `jobStatus`.
- **Flow bar** (`receivedProposalFlowSteps`): PENDING → 제안 「결정해 주세요」,
  AWAITING_START → 시작 「학생 시작 전」, ACCEPTED → 초안 「작업 중」. Cancelled
  or rejected proposals have no bar.
- **Student line** (`studentMetaText`): 「24학번 · 전공」. A 10-digit number
  becomes its third and fourth digits; a two-digit number is used as is.
  Missing parts are left out.
- **List card** (all statuses):
  - Shows the chip, `likeCount`, one badge per category, 「M월 D일 도착」
    when `createdAt` exists, the solution excerpt, and the student line.
  - PENDING cards keep 「제안 받기」, which opens the detail.
- **Detail**:
  - Heading: chip, badges, and 「M월 D일 도착」. A missing `createdAt` hides
    the date.
  - Empathy line from `likeCount`.
  - Student box: the student line and 「★ 4.8 · 완료 3건」, or 「첫 작업이에요」
    when there is no rating or completed job.
  - After payment, 「정한 작업 조건」 shows 작업비, 초안 마감, 최종 마감,
    수정 횟수, and 「학생에게 한마디」 when it is not blank.
  - PENDING shows 「수락하면 M월 D일까지 초안, M월 D일까지 최종」 and the old
    footnote.
  - Reference photos use `ReferencePhotos`.
  - Footer: 「의뢰하기」 for PENDING (to the accept screen), otherwise
    「확인」.
  - Errors: load failure → `LoadNotice`, 404 → `OwnerMissing`,
    401 → /login.
- **Hidden until their APIs exist**: 「거절하기」 (list and detail) and the
  student 「프로필 보기」 (list and detail). They are removed rather than
  left as dead code. `OWNER_PATHS.student` and the student profile page stay
  for the applicant flow.
- **Accept screen** (`OwnerProposalAcceptPage`, minimal change):
  - It reads the proposal with `useProposalDetail` instead of the sample, so
    a real id no longer shows 「찾는 내용이 없어요」.
  - It shows the title, category badges, student name, 희망 작업비, and
    「예상 초안 N일 · 최종 N일」 from the detail.
  - Loading and failure show `LoadNotice` with 「다시 시도」; 404 shows
    `OwnerMissing`; 401 goes to /login. These are the same rules as the
    detail page.
  - A proposal that is not PENDING redirects (history replaced) to its
    detail, where the status chip explains why. The backend would refuse
    payment anyway (409 `PROPOSAL_409_PAYMENT`).
  - The form mounts only after the proposal loads, so 작업비 starts at
    `proposedFee`.
  - The form and 「안전결제하기」 are unchanged: payment is still the
    `useSafePayment` stand-in.
- **Sample data removed**: `SAMPLE_PROPOSALS`, `useOwnerProposal`,
  `useOwnerProposals`, the `OwnerProposal` type, and `counts.proposals` in
  `useOwnerProfile`.
- **Shared types widened**: the owner `TodoBase.field` is a string, so server
  category names fit.

## Rationale

- One shared detail feature keeps both roles on the same request, 404, and
  401 rules, as the specialty list already does.
- Showing every status keeps proposals the owner already paid for findable;
  the chip tells them apart.
- Removing buttons that would do nothing matches how the student cancel popup
  was removed (ADR 0023).

## Alternatives Considered

- Keeping the detail code in the student feature and importing it from the
  owner feature: rejected, the owner would depend on student internals and
  their naming.
- Only PENDING in the 받은 제안 tab: rejected by the request; paid proposals
  would vanish before the 진행 중 tab is wired.

## Agent Guidance

- When the backend adds `createdAt` and `jobStatus` to
  `GET /me/received-proposals`, the list shows them with no code change.
  Remove the 「백엔드에 추가 요청함」 notes in `receivedProposalApi.ts`.
- Wire 「거절하기」 and 「프로필 보기」 when their APIs arrive, and the accept
  screen's payment with `POST /proposals/{id}/payments`. That payment takes
  only `revisionCount`, `messageToStudent`, and `refundPolicyAgreed`; fee and
  deadlines come from the proposal, so the editable 작업비 and 마감일 fields
  must be revisited then.
- Owner notifications still use sample ids such as `prop-201`. Since the
  detail is now an API call, those links show 「없음」 until notifications
  are wired.
- The owner pages import `LoadNotice` and `expectedDaysText` from the student
  feature. Move them to a shared layer if more owner screens need them.

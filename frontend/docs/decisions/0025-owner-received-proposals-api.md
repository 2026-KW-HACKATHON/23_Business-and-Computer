# 0025. Owner received proposals call the backend API

## Status

Accepted. Replaces the sample `OwnerProposal` data (deleted) for 내 활동 ›
받은 제안, the received-proposal detail, the proposal summary on the accept
screen, the 「새 제안」 cards in the owner home's 확인할 일, and the 내 정보
받은 제안 count (ADR 0012, 0017). Moves the proposal detail code that both
roles use out of the student feature (ADR 0023).

## Context

The owner side read `SAMPLE_PROPOSALS`, so a proposal a student sent
(ADR 0020) never reached the owner. The backend (dev) has:

- `GET /me/received-proposals` → `{ data: { proposals: [...] } }`, newest
  first, every status, no paging. Each item has these fields:
  - `proposalId`, `title`, `status`, `likeCount`, `specialtyCategories`,
    `proposedSolution`;
  - `student` (`studentProfileId`, `name`, `studentNumber`, `major`). Here
    `studentNumber` is the full 10 digits;
  - `jobId`, which is null before payment.
  - The list has no `createdAt` and no `jobStatus`.
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
- `POST /proposals/{id}/payments` takes `budget` (the fee the owner sets),
  `revisionCount`, `messageToStudent`, and `refundPolicyAgreed`. The server
  sets the deadlines to the payment date plus `draftDays` / `finalDays`.
- The backend has no endpoint to reject a proposal or to read the proposing
  student's profile.

## Decision

- **Shared proposal feature** `src/features/proposal`, following the
  `src/features/specialty` pattern (ADR 0020):
  - Exports `useProposalDetail` (loading / error / notFound / loaded; 401 →
    /login) and the types that other features use (`ProposalDetail`,
    `ProposalStatus`, `ProposalJobStatus`, `ProposalSpecialtyCategory`,
    `ProposalStudentResponse`).
  - Exports the display helpers `proposalBadgeNames`,
    `estimatedDeadlineText`, `expectedDaysText` (「수락 후 초안 N일 · 최종 N일」), and
    `proposalMonthDay`, which turns the text of `createdAt` into 「M월 D일」.
  - The request, error mapping, and id parsing (`fetchProposalDetail`,
    `loadProposalDetail`, `parseProposalId`) stay inside the feature.
  - The student feature keeps only the student wording (`sentOnText`, status
    chip, flow bar) and `useSentProposals`. Student behavior is unchanged.
- **`LoadNotice`** (one loading or 「다시 시도」 line) lives in
  `src/components/LoadNotice`. Owner and student screens import it from
  there.
- **Owner code** in `src/features/owner`:
  - `src/features/owner/api/receivedProposalApi.ts` (`fetchReceivedProposals`,
    using `apiData`). `createdAt` and `jobStatus` are optional fields, used
    when the server sends them.
  - `src/features/owner/lib/receivedProposals.ts`: result values and display
    helpers.
  - `src/features/owner/hooks/useReceivedProposals.ts`: loading / error /
    loaded with `reload`.
    - It drops late responses.
    - 401 goes to /login.
    - `PROPOSAL_403_LIST_OWNER` or `OWNER_403` shows the alert 「사장님만 받은
      제안을 볼 수 있어요」, then goes to `landingPath()`.
- **One hook for three places**: 내 활동 › 받은 제안, the home 「확인할 일」,
  and the 내 정보 count all use `useReceivedProposals`.
  - The summary counts read 「-」 while loading or after a failure.
  - 내 활동 shows `LoadNotice` with 「다시 시도」.
  - Home: only PENDING proposals become 「새 제안」 cards. The card badge is
    the first category name, and the student line shows `major`. While the
    list loads or after a failure, 확인할 일 shows `LoadNotice` and no count.
  - `firstVisit` comes from the sample flag.
- **Status chip** (`receivedProposalStatusLabel`):
  - PENDING 「결정 대기」, AWAITING_START 「결제 완료」, ACCEPTED 「작업 중」,
    REJECTED · CANCELLED 「성사되지 않음」.
  - Any other status 「확인 필요」, so the chip is never blank.
  - A CANCELLED job status overrides them with 「성사되지 않음」. On the detail that
    comes from `agreement.jobStatus`; list cards use `jobStatus` only when
    the server sends it.
  - A CLOSED job status shows 「완료」: the server keeps a finished proposal at
    ACCEPTED and only the job closes.
  - 「작업 중」 (ACCEPTED with the job neither CLOSED nor CANCELLED,
    `receivedProposalInProgress`) uses the owner color (`--color-main`) so it
    stands apart from the grey chips, on the 받은 제안 list and the detail.
- **Flow bar** (`receivedProposalFlowSteps`): PENDING → 제안 「결정해 주세요」,
  AWAITING_START → 시작 「학생 시작 전」, ACCEPTED → 초안 「작업 중」, a CLOSED job →
  every step done. Cancelled, rejected, and unknown statuses have no bar.
- **Student line** (`studentMetaText`): 「24학번 · 전공」. A 10-digit number
  becomes its third and fourth digits; a two-digit number is used as is.
  Missing parts are left out.
- **List card** (all statuses):
  - Shows the chip, `likeCount`, one badge per category, 「M월 D일 도착」
    when `createdAt` exists, the solution excerpt, and the student line.
  - PENDING cards have 「자세히 보고 수락하기」, which opens the detail.
- **Detail**:
  - Heading: chip, badges, and 「M월 D일 도착」. A missing `createdAt` hides
    the date.
  - Empathy line from `likeCount`.
  - Student box: the student line and 「★ 4.8 · 완료 3건」, or 「첫 작업이에요」
    when there is no rating or completed job.
  - After payment, 「정한 작업 조건」 shows 작업비, 초안 마감, 최종 마감,
    수정 횟수, and 「학생에게 한마디」 when it is not blank.
  - PENDING shows:
    - 「수락하면 M월 D일까지 초안, M월 D일까지 최종」;
    - the footnote: the owner sets 작업비 (from the student's hoped amount)
      and 수정 횟수 when accepting, and 마감일 counts the student's period
      from the payment date.
  - Reference photos use `ReferencePhotos`.
  - Footer: 「수락하기」 for PENDING (to the accept screen), otherwise
    「확인」.
  - Errors: load failure → `LoadNotice`, 404 → `OwnerMissing`,
    401 → /login.
- **Actions**:
  - The received-proposal screens have no 「거절하기」 and no student
    「프로필 보기」.
  - `OWNER_PATHS.student` and the student profile page serve the applicant
    flow.
- **Accept screen** (`OwnerProposalAcceptPage`):
  - **Data**:
    - It reads the proposal with `useProposalDetail`.
    - It shows the title, category badges, student name, 희망 작업비, and
      「예상 초안 N일 · 최종 N일」.
  - **작업비**: a `BudgetField` filled with `proposedFee`, with 「학생 희망
    작업비를 참고해 정해 주세요」. The owner can change it.
  - **Read-only field**: 마감일 shows 「초안 N일 · 최종 N일」 with 「학생이 제안한 기간 · 결제한
      날부터 세요」.
  - **Inputs**: 수정 횟수, 학생에게 한마디, and the refund-policy agreement.
    「안전결제하기」 needs the agreement and a 작업비 above 0.
  - **Payment**: the payment amount is the 작업비 the owner set. 「안전결제하기」 pays
    through KakaoPay (ADR 0031).
  - **Errors**: loading and failure show `LoadNotice` with 「다시 시도」; 404
    shows `OwnerMissing`; 401 goes to /login. These are the same rules as
    the detail page.
  - **Not PENDING**: the screen redirects (history replaced) to the detail,
    where the status chip shows the state. The server refuses payment for it
    with 409 `PROPOSAL_409_PAYMENT`.
- **Notification**: 「새 제안이 왔어요」 (`PROPOSAL_RECEIVED`) opens 내 활동 ›
  받은 제안 (`OWNER_PATHS.activity("proposals")`).
- **Shared types widened**: the owner `TodoBase.field` is a string, so server
  category names fit.

## Rationale

- One shared detail feature keeps both roles on the same request, 404, and
  401 rules, as the specialty list already does.
- Moving `LoadNotice` and `expectedDaysText` out of the student feature keeps
  owner screens from depending on student code.
- Showing every status keeps proposals the owner already paid for findable;
  the chip tells them apart.
- Read-only 작업비 and 마감일 show exactly what the server charges and
  schedules; editable fields would suggest values the payment ignores.
- The screens show only actions the backend supports.

## Alternatives Considered

- Keeping the detail code in the student feature and importing it from the
  owner feature: rejected, the owner would depend on student internals and
  their naming. The first version of this change still imported
  `LoadNotice` and `expectedDaysText` from the student feature; they were
  moved for the same reason.
- Only PENDING in the 받은 제안 tab: rejected; paid proposals would disappear
  from every owner list.
- Opening the proposal detail from the 「새 제안」 notification: rejected, the
  sample notification has no real proposal id, so the detail would show
  「없음」.

## Agent Guidance

- `GET /me/received-proposals` items may carry `createdAt` and `jobStatus`;
  the list shows them whenever they are present.
- The accept screen must not offer fee or deadline inputs: the payment API
  takes only `revisionCount`, `messageToStudent`, and `refundPolicyAgreed`.
- Keep `src/features/proposal/index.ts` limited to what other folders import.

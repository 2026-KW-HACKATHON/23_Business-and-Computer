# 0022. Student sent proposals call the backend API

## Status

Accepted. Replaces the sample `MyProposal` data for 내 활동 › 보낸 제안, the
sent-proposal detail, the home 「기다리는 중」 proposals, and the 내 정보
보낸 제안 count (ADR 0018). Follows ADR 0021, which sends the proposal.

## Context

A proposal sent through `POST /proposals` (ADR 0021) did not show in any
student screen, which still read `sampleProposals.ts`. The backend (dev) has:

- `GET /me/proposals` → `{ data: { proposals: [...] } }`, newest first, all
  statuses. Each item: `proposalId`, `title`, `status`, `likeCount`,
  `specialtyCategories` (`[{ id, name, specialties: [{ id, name }] }]`),
  `proposedSolution`, `store` (`ownerProfileId`, `storeName`,
  `storeAddress`, `profileImageUrl`), and `jobId` (null before payment).
  There is no `createdAt` (requested from the backend) and no job status.
  Errors: 401 `COMMON_401`, 403 `PROPOSAL_403_LIST_STUDENT` (not a student,
  or no student profile).
- `GET /proposals/{proposalId}` → one proposal for any active user:
  `title`, `storeName` (no address), `likeCount`, `specialtyCategories`,
  `student`, `customerProblem`, `proposedSolution`, `workPlan`,
  `proposedFee`, `draftDays`, `finalDays`, `referenceImageUrls`, `createdAt`
  (a zone-less `LocalDateTime`), `status`, `estimatedDraftDeadline` /
  `estimatedFinalDeadline` (dates, PENDING only, counted from today in
  Asia/Seoul), `jobId`, and `agreement` (`jobStatus`, `budget`,
  `draftDeadline`, `finalDeadline`, `revisionCount`, `messageToStudent`,
  `paidAt`, `startedAt`; only after payment and only for the owner and the
  student of that proposal). Errors: 401, 404 `PROPOSAL_404`.
- Status: `PENDING` (before payment), `AWAITING_START` (owner paid, student
  has not started), `ACCEPTED` (student started). `REJECTED` exists but no
  code sets it. A job the owner cancels has `jobStatus` `CANCELLED`.
- There is no API to cancel a proposal or to read empathy beyond `likeCount`.

## Decision

- **Calls and mapping**: `fetchMyProposals` and `fetchProposalDetail` in
  `src/features/student/api/proposalApi.ts` use `apiData`.
  `src/features/student/lib/sentProposals.ts` maps errors to result values
  (`loadSentProposals`, `loadSentProposalDetail`) and holds the display
  helpers. The screens use the response shapes directly
  (`SentProposal`, `SentProposalDetail`).
- **Hooks** (`src/features/student/hooks/useSentProposals.ts`):
  - `useSentProposals` → loading / error / loaded with `reload`. It drops late
    responses. 401 goes to /login. `PROPOSAL_403_LIST_STUDENT` shows the
    alert 「학생만 보낸 제안을 볼 수 있어요」, then goes to `landingPath()`.
    The status stays loading until the redirect happens.
  - `useSentProposalDetail(id)` → loading / error / notFound / loaded. A
    non-numeric id is notFound without a request. Each result is keyed by id
    and retry count, so an old result never shows for a new id. 401 goes to
    /login.
  - `apiData` already retries `POST /refresh` once and clears the stored
    tokens when that fails. The hooks only send the user to /login.
- **One hook for four screens**: 내 활동 › 보낸 제안, the home 「기다리는 중」,
  and the 내 정보 count all use `useSentProposals`.
  - 내 활동 and 내 정보: the summary count reads 「-」 while loading or after a
    failure. 내 활동 shows `LoadNotice` in place of the list, with
    「다시 시도」 on failure. 「아직 없어요」 shows only for a loaded empty list.
  - Home: 「기다리는 중」 lists PENDING proposals. While the list loads or
    after a failure, that section shows `LoadNotice` with no count.
  - First-visit check on the home: the screen is not a first visit when there
    are works or applications. Otherwise it is decided only once the sent list
    has loaded. Until then the home shows only the loading or retry line,
    never the guide.
  - 탐색 「내 제안」 still uses the sample data and is wired together with
    /explore.
- **Status chip** (`sentProposalStatusLabel`):
  - PENDING 「수락 대기 중」, AWAITING_START 「수락됨」, ACCEPTED 「작업 중」,
    REJECTED 「거절됨」.
  - `agreement.jobStatus` CANCELLED overrides all of them with 「취소됨」.
  - Every status stays in the list. The list has no job status, so 「취소됨」
    can show only on the detail, which now has a chip in its heading.
- **Flow bar** (`sentProposalFlowSteps`): PENDING → 제안 「수락 대기」,
  AWAITING_START → 시작 「시작 전」, ACCEPTED → 초안 「작업 중」. A cancelled or
  rejected proposal shows no bar.
- **List card**:
  - Shows the chip, `likeCount`, one badge per category name, the solution
    excerpt, and the store name and address.
  - No sent date, until the backend adds `createdAt`.
  - No 「조건 확인하기」; starting the work is the next issue.
- **Detail**:
  - Heading: the chip, all category badges, and 「M월 D일 보냄」. The date is
    read from the text of `createdAt`, so the browser time zone cannot shift
    it.
  - Empathy: only 「학생 손님 N명이 공감했어요」 from `likeCount`. The
    「공감이 많이 모이면 …」 line is gone, because nothing implements it.
  - Store box: name only. The store address arrives through router state
    (`SentProposalRouteState`) from 내 활동 and the home; with no address
    the line is hidden. The 「사장님이 확인했어요 / 아직 확인하지 않았어요」
    note is removed, since no API exposes it.
  - AWAITING_START or ACCEPTED with an `agreement` shows 「사장님이 정한 작업
    조건」: 작업비, 초안 마감, 최종 마감, 수정 횟수, and 「사장님 메시지」 when
    it is not blank.
  - PENDING shows 「수락하면 M월 D일까지 초안, M월 D일까지 최종」 from the
    estimated deadlines, plus the old footnote.
  - Reference photos use the new `ReferencePhotos` component
    (`src/components/ReferencePhotos`). It draws square thumbnails that open
    the original image in a new tab. A photo that fails to load becomes a grey
    tile. `AttachmentTiles` is unchanged.
  - Footer: 「확인」 only. 「제안 취소」 is hidden. The cancel confirm and
    done dialogs stay in the page for when a cancel API exists, and the
    confirm handler has a note to call it.
  - Errors: load failure → `LoadNotice` 「다시 시도」, 404 → `StudentMissing`,
    401 → /login.
- **Categories**: one badge per category, so a 「기타」 proposal shows a
  single 「기타」 badge. `CategoryBadge` now takes any string, because server
  category names need not match the Figma `Field` list.
- **After sending**: 4/4 passes `{ proposalId }` to the done screen
  (`ProposalDoneState`). 「확인」 then replaces the history entry with
  /student/proposals/{id}. Without that state it goes to 내 활동 › 보낸 제안.
- `SummaryCard` counts accept a string, used for the 「-」.

## Rationale

- One hook keeps the four places in step and makes a redirect happen in one
  place.
- Hiding the first-visit guide until the list loads avoids flashing a
  「처음이에요」 screen at a student who has proposals.
- Hiding actions that have no API, instead of faking them, avoids buttons that
  appear to work but change nothing on the server.

## Alternatives Considered

- Loading each detail from the list to find cancelled jobs: rejected, one
  request per card; asking the backend to add the job status to the list is
  cheaper.
- Showing 0 while loading: rejected, it looks like a real count and would make
  the home pick the first-visit guide.
- Reusing `AttachmentTiles` for real photos: rejected by the request; it stays
  a name tile for sample data.

## Agent Guidance

- When the backend adds `createdAt` (and ideally `agreement.jobStatus`) to
  `GET /me/proposals`, show the sent date on list cards and pass the job
  status to `sentProposalStatusLabel` there.
- Wire 「조건 확인하기」 (`POST /jobs/{jobId}/start`) in the next issue, from
  the AWAITING_START detail.
- Connect 「제안 취소」 to the dialogs in `StudentProposalPage` once a cancel
  API exists.
- `useMyProposal` (sample) is still used by the work-start screen;
  `useMyProposals` was removed because nothing else used it.
- `GET /proposals/{id}` does not check that the viewer wrote the proposal; the
  screen trusts that it was reached from the student's own list.

# 0023. Student sent proposals call the backend API

## Status

Accepted. Replaces the sample `MyProposal` data for 내 활동 › 보낸 제안, the
sent-proposal detail, the home 「기다리는 중」 proposals, and the 내 정보
보낸 제안 count (ADR 0018). Follows ADR 0020, which sends the proposal.

## Context

A proposal sent through `POST /proposals` (ADR 0020) did not show in any
student screen, which still read `sampleProposals.ts`. The backend (dev) has:

- `GET /me/proposals` → `{ data: { proposals: [...] } }`, newest first, all
  statuses. Each item: `proposalId`, `title`, `status`, `likeCount`,
  `specialtyCategories` (`[{ id, name, specialties: [{ id, name }] }]`),
  `proposedSolution`, `store` (`ownerProfileId`, `storeName`,
  `storeAddress`, `profileImageUrl`), `jobId` and `jobStatus` (null before
  payment), and `createdAt` (Korea time, no offset). `jobStatus` and
  `createdAt` were added on the backend after this ADR was first written.
  Errors: 401 `COMMON_401`, 403 `PROPOSAL_403_LIST_STUDENT` (not a student,
  or no student profile).
- `GET /proposals/{proposalId}` → one proposal for any active user in the
  same demo session (another session's proposal is 404):
  `title`, `storeName`, `storeAddress` (the store's current address, null if
  not set; added later), `likeCount`, `specialtyCategories`,
  `student`, `customerProblem`, `proposedSolution`, `workPlan`,
  `proposedFee`, `draftDays`, `finalDays`, `referenceImageUrls`, `createdAt`
  (Korea time, no offset), `status`, `estimatedDraftDeadline` /
  `estimatedFinalDeadline` (dates, PENDING only, counted from today in
  Asia/Seoul), `jobId`, and `agreement` (`jobStatus`, `budget`,
  `draftDeadline`, `finalDeadline`, `revisionCount`, `messageToStudent`,
  `paidAt`, `startedAt`; only after payment and only for the owner and the
  student of that proposal). Errors: 401, 404 `PROPOSAL_404`.
- Status: `PENDING` (before payment), `AWAITING_START` (owner paid, student
  has not started), `ACCEPTED` (student started), `REJECTED` (the student
  declined the paid request), `CANCELLED` (the student cancelled before
  payment). A cancelled or declined job has `jobStatus` `CANCELLED`.
- POST /proposals/{id}/cancel cancels a PENDING proposal (ADR 0033). There is
  no API to read empathy beyond `likeCount`.

## Decision

- **Calls and mapping**: `fetchMyProposals` in
  `src/features/student/api/proposalApi.ts` uses `apiData`.
  `src/features/student/lib/sentProposals.ts` maps errors to result values
  (`loadSentProposals`) and holds the student display helpers. The detail
  call, its types, `loadProposalDetail`, `parseProposalId`,
  `proposalBadgeNames`, and `estimatedDeadlineText` moved to the shared
  `src/features/proposal` when the owner side was wired (ADR 0025). The
  screens use the response shapes directly (`SentProposal`,
  `ProposalDetail`).
- **Hooks** (`src/features/student/hooks/useSentProposals.ts`):
  - `useSentProposals` → loading / error / loaded with `reload`. It drops late
    responses. 401 goes to /login. `PROPOSAL_403_LIST_STUDENT` shows the
    alert 「학생만 보낸 제안을 볼 수 있어요」, then goes to `landingPath()`.
    The status stays loading until the redirect happens.
  - `useProposalDetail(id)` (shared, ADR 0025; first written here as
    `useSentProposalDetail`) → loading / error / notFound / loaded. A
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
    are works, applications, or sent proposals. Otherwise it is decided only
    once the sent list and the application list (ADR 0027) have loaded. Until then the home shows only the loading or retry line,
    never the guide.
  - 탐색 marks 「내 제안」 by the ids in this list (ADR 0026).
- **Status chip** (`sentProposalStatusLabel`):
  - PENDING 「수락 대기 중」, AWAITING_START 「수락됨」, ACCEPTED 「작업 중」,
    REJECTED · CANCELLED 「성사되지 않음」.
  - A CANCELLED job status overrides all of them with 「성사되지 않음」: the
    list's `jobStatus` on cards, `agreement.jobStatus` on the detail.
  - A CLOSED job status shows 「완료」: the server keeps a finished proposal at
    ACCEPTED and only the job closes.
  - 「작업 중」 (ACCEPTED with the job neither CLOSED nor CANCELLED,
    `sentProposalInProgress`) uses a light student color so it stands apart
    from the grey chips, on the 보낸 제안 list and the detail.
  - Every status stays in the list. The detail has the same chip in its
    heading.
- **Flow bar** (`sentProposalFlowSteps`): PENDING → 제안 「수락 대기」,
  AWAITING_START → 시작 「시작 전」, ACCEPTED → 초안 「작업 중」, a CLOSED job →
  every step done. A cancelled or rejected proposal shows no bar.
- **List card**:
  - Shows the chip, `likeCount`, one badge per category name, the solution
    excerpt, 「M월 D일 보냄」, and the store name and address.
  - The sent date or the address line is hidden when its value is missing
    (`sentOnText`, `storeAddressText`).
  - AWAITING_START with the job not cancelled shows 「조건 확인하기」, which
    opens the work-start screen (ADR 0029).
- **Detail**:
  - Heading: the chip, all category badges, and 「M월 D일 보냄」. The date is
    read from the text of `createdAt` (already Korea time), so the browser
    time zone cannot shift it. No `createdAt` hides the date.
  - Empathy: 「학생 손님 N명이 공감했어요」 from `likeCount` and the Figma line
    「공감이 많이 모이면 사장님께 한 번 더 알려 드려요」.
  - Store box: name and `storeAddress`; with no address the line is hidden.
    AWAITING_START or ACCEPTED adds 「사장님이 제안을 받아들였어요」 under it.
  - AWAITING_START or ACCEPTED with an `agreement` shows 「사장님이 정한 작업
    조건」: 작업비, 초안 마감, 최종 마감, 수정 횟수, and 「사장님 메시지」 when
    it is not blank.
  - PENDING shows 「수락하면 M월 D일까지 초안, M월 D일까지 최종」 from the
    estimated deadlines, plus the old footnote.
  - Reference photos use the new `ReferencePhotos` component
    (`src/components/ReferencePhotos`). It draws square thumbnails with a file
    name badge (the given `names`, else the URL's last segment, else 「사진
    n」 for a random server name); a tap opens the photo large in the app
    (`PhotoViewer`: close with ✕, the backdrop, or Esc; ‹ › and the arrow
    keys move between photos). A photo that fails to load becomes a grey
    tile. `AttachmentTiles` is unchanged.
  - Footer: 「조건 확인하기」 for AWAITING_START with the job not cancelled
    (ADR 0029); 「제안 취소」 (gray) and 「확인」 while PENDING and the job is
    not cancelled; otherwise 「확인」. 「제안 취소」 opens 「제안을 취소할까요?」
    (Figma 「제안 취소 확인」); 「제안 취소하기」 cancels and opens 「제안을
    취소했어요」 (ADR 0033).
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
  request per card; the backend added `jobStatus` to the list instead.
- Passing the store address from the list through router state: rejected,
  a detail opened from the done screen, a notification, or a link has no
  list behind it.
- Showing 0 while loading: rejected, it looks like a real count and would make
  the home pick the first-visit guide.
- Reusing `AttachmentTiles` (name tiles) for real photos: rejected by the
  request.

## Agent Guidance

- `GET /proposals/{id}` does not check that the viewer wrote the proposal
  (only the demo session); the screen trusts that it was reached from the
  student's own list.

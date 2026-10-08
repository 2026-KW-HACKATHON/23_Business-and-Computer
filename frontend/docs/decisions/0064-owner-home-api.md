# 0064. The owner home reads GET /me/home

## Status

Accepted. The owner home (/owner) builds 확인할 일, 학생이 작업 중, 기다리는 중,
끝난 일, and the first-visit state from one call, GET /me/home. It no longer
reads the open, received-proposal, in-progress, and closed lists itself. Those
lists and their hooks stay for 내 활동, 내 정보, 탐색, and the work screens.

## Context

- The home made four calls (GET /me/jobs?status=OPEN, GET
  /me/received-proposals, GET /me/jobs?status=MATCHED with the received
  proposals again, GET /me/jobs?status=CLOSED) and sorted, filtered, and
  combined them in `useOwnerHome` (ADR 0025, 0030, 0035, 0036). It worked out
  the auto-complete date (arrival + 7 days, ADR 0039) and the first visit
  (ADR 0051) itself.
- The backend added GET /me/home (any signed-in user, the body depends on the
  role; 403 HOME_403 before signup is finished). The owner body has `role`
  「OWNER」, `firstVisit`, `todos`, `working`, `waiting`, and `done`. Every
  field is always present. A section that is null failed to load on the
  server; an empty list loaded empty. Dates are Korean dates (YYYY-MM-DD).
  - `todos`: arrived drafts (current due first), then PENDING proposals, then
    open requests with applicants (draft deadline first). Each item carries
    only the fields of its `type` (draftArrived: `jobId`, `revision`,
    `autoCompleteOn`; proposalArrived: `proposalId`, `likeCount`;
    applicants: `jobId`, `applicantCount`, `draftDeadline`), plus `kind`,
    `title`, `field` (first category name), and `student`.
  - `working`: in-progress jobs with nothing waiting for review, by the
    current due (`stage` draft or final, `due`).
  - `waiting`: open requests with no applicants, by draft deadline.
  - `done`: completed jobs only, latest first, with `completedOn`.
  - `firstVisit`: true when every source loaded empty, false when any source
    has history, null when nothing was seen and a source failed.

## Decision

- **API** (`src/features/owner/api/homeApi.ts`): `fetchOwnerHome` calls GET
  /me/home through `apiData` and returns the body only when `role` is
  「OWNER」.
- **Mapping** (`src/features/owner/lib/ownerHome.ts`, `loadOwnerHome`): the
  server order is kept. A todo opens by its id: draftArrived → 작업 확인
  (`jobId`), proposalArrived → 받은 제안 상세 (`proposalId`), applicants →
  지원자 목록 (`jobId`); an item without that id, or of an unknown type, is
  left out. A missing `field` shows 「기타」. The card's auto-complete date is
  the server `autoCompleteOn`. 학생이 작업 중 rows open 보낸 의뢰 or, with a
  `proposalId`, the received proposal (ADR 0049); 기다리는 중 rows open 보낸
  의뢰; 끝난 일 rows open 지난 결과물. Texts are unchanged.
- **First visit**: the server `firstVisit` decides. true shows the 「처음」
  card (ADR 0051); false or null shows the normal home, as a failed list did
  before.
- **Loading and errors** (`useOwnerHome`, `src/pages/OwnerHomePage.tsx`):
  - While the call is pending, or when it fails, 확인할 일 shows one
    `LoadNotice` (「홈을 불러오는 중이에요」 · 「홈을 불러오지 못했어요」) and the
    other sections hide.
  - When the call succeeds, each section that came back null shows its own
    `LoadNotice` in its place, with no count (확인할 일, 학생이 작업 중, 기다리는
    중, 끝난 일). 「다시 시도」 calls GET /me/home again; the loaded sections
    stay on screen and the failed ones show the skeleton until the answer.
  - Sections that loaded empty hide, except 확인할 일, which shows the
    「없음」 card (ADR 0051).
  - 401 goes to /login; HOME_403 goes to /signup/role.

## Rationale

- One request, sorted and filtered on the server, replaces four lists and the
  client-side merge, and the home shows what the backend counts as 할 일.
- Keeping the home view model (`OwnerHome`) let the cards, rows, and links
  stay as they were.
- Per-section notices follow the server's partial-failure contract, so one
  failed source no longer hides the rest of the home.

## Alternatives Considered

- Keep the four list calls: more requests, and the client would keep a
  second copy of the ordering and first-visit rules.
- Hide a failed section, as 끝난 일 did: the owner could not tell an empty
  section from a failed one, and there was no way to retry.
- Retry only the failed section: the endpoint has no per-section call, so the
  retry asks for the whole home.

## Agent Guidance

- Change the home's order, filters, or first-visit rule in the backend, not
  in `useOwnerHome`.
- A new owner home section joins `OwnerHome.sections` and gets its own
  `LoadNotice` for a null section.
- Do not move other screens to GET /me/home; 내 활동 and the work screens keep
  their list hooks.

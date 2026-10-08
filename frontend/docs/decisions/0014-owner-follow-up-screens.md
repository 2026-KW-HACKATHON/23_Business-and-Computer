# 0014. Owner screens after the first level

## Status

Accepted

## Context

ADR 0013 built the screens one tap away from the owner home and left the
next level falling back to /owner. Figma 「2. 사장님」 sections A–H and the
Notion 「화면 상태 전환표」 describe the rest of the owner flow: store edit,
payments and 내 활동, request registration 2/3 · 3/3, choosing and paying
a student, accepting a proposal, revision and review, cancelling, and the
read-only explore details. The backend has no API for them yet.

## Decision

- Routes follow Notion: /owner/me/store, /owner/me/payments,
  /owner/requests?tab=,
  /owner/requests/:requestId/assign/:applicationId and its /pay (ADR 0037),
  /owner/proposals/:proposalId/accept, /owner/works/:workId/revision,
  /owner/works/:workId/review, /owner/works/:workId/cancel,
  /explore/proposals/:proposalId, and /explore/requests/:requestId.
  Additions without a Notion row: /owner/requests/new/2, /3, and /done
  (like the signup steps), /owner/works/:workId/review/done, and
  /owner/works/:workId/canceled.
- Request registration passes its values in router state
  (`src/features/owner/lib/newRequest.ts`). Before moving on, each step
  saves its values into its own history entry, so ← and 「내용 고치기」
  keep what was typed. An example card fills step 2 from the example.
- 이 학생에게 맡기기 and the request pay screen read the job and the chosen
  application from the backend and pay through KakaoPay (ADR 0037);
  proposal accept pays through KakaoPay too (ADR 0031).
- Popups follow Notion, not the Figma prototype, where they differ:
  revision sent → 내 의뢰 (진행 중), payment done → 내 의뢰 (진행 중) (the
  proposal payment goes to the proposal detail, ADR 0031), request registered
  → 내 의뢰 (보낸 의뢰).
- Cancelling keeps the 20% start reward from Notion 「취소·환불 정책」
  (`startReward`). Reporting a student opens the mail popup with a
  prefilled mailto link; there is no in-app form.
- Explore cards and the explore request detail show the store name
  only, without the street or address. The read-only details hide budget
  and deadlines, and 「우리 가게에도 비슷한 의뢰 만들기」 opens registration
  with the same categories picked, and for a request also its tasks
  (ADR 0058).
- 탐색 reads the backend (ADR 0026), and so does the student profile
  (ADR 0038).
- New shared components: `Dialog`, `DoneScreen`, `NumberedSteps`,
  `StarRating`, and `TrustChips`. `Chip` takes `tone` so an outlined chip
  turns owner yellow when picked.

## Rationale

- Saving each step's values in history keeps the steps as plain routes
  without a store, and a refresh mid-way sends the owner back to step 1
  like the signup flow.
- One `PaymentSection` serves both the pay screen and proposal accept,
  which share the amount box, methods, and agreement.

## Alternatives Considered

- One route with a step state for registration (the Notion suggestion):
  rejected to match the signup steps and keep ← working per step.
- A real work id before payment: not possible until select_applicant
  exists on the backend.

## Agent Guidance

- Backend integration: replace the hooks in `useOwnerData.ts`, and save
  reviews, revisions, and cancellations on the server. Choosing and paying
  for an applicant read and write the backend (ADR 0037).
- 거절하기 on a received proposal and on its 내 활동 card rejects through the
  API (ADR 0033). 「약관 및 정책」 in 내 정보 opens `TermsSheet`.

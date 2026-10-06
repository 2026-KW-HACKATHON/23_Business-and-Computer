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
  /owner/requests?tab=, /owner/students/:studentId,
  /owner/requests/:requestId/assign/:studentId, /owner/works/:workId/pay,
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
- Until the backend creates a work when a student is chosen, the pay
  route uses 「request id~student id」 as the work id
  (`src/features/owner/lib/checkout.ts`).
- The request pay screen uses a mock (`useSafePayment`): the redirect
  screen succeeds after 1.5 seconds, and tapping it shows the failure popup,
  as in the Figma prototype. Proposal accept pays through KakaoPay
  (ADR 0031).
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
  with the same field and tasks picked.
- Sample data stays behind the hooks in
  `src/features/owner/hooks/useOwnerData.ts`; student profiles live in
  `src/features/owner/lib/sampleStudents.ts`. 탐색 reads the backend
  (ADR 0026).
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

- Backend integration: replace the hooks in `useOwnerData.ts`, create
  the work on 「네, 맡길게요」 and use its id for the pay route, pay through
  `startKakaoPay` (ADR 0031) instead of `useSafePayment`, and save
  reviews, revisions, and cancellations on the server.
- Still without an action: 거절하기 on proposals, rejecting from 내 활동,
  and 알림 설정 · 계정 정보 · 약관 in 내 정보.

# 0033. Proposal decline and cancel call the APIs, and every cancelled deal reads 「성사되지 않음」

## Status

Accepted. 「이 조건은 어려워요」 → 「거절하기」 on the work-start screen,
「제안 취소하기」 on the sent-proposal detail, and the owner's 「거절하기」 on a
received proposal call the backend. Every status
chip for a deal that did not go through reads 「성사되지 않음」. The work-start
screen shows the fee the owner set next to the student's wished fee.

## Context

The backend (dev) has:

- POST /jobs/{jobId}/decline (no body) for a paid proposal job that has not
  started (AWAITING_START). It cancels the job, marks the proposal REJECTED,
  and refunds the owner in full (student compensation 0). Errors:
  JOB_DECLINE_403 (not the proposing student), JOB_DECLINE_409 (already
  started, declined, or closed), JOB_404.
- POST /proposals/{proposalId}/cancel for a proposal before payment
  (PENDING). It marks the proposal CANCELLED and removes its likes; sending
  it again returns the same result. A cancelled proposal leaves the lists and
  explore, and only its student can still read it. Errors:
  PROPOSAL_403_CANCEL, PROPOSAL_409_CANCEL (not PENDING),
  PROPOSAL_409_CANCEL_PAYMENT_PENDING (the owner is paying), PROPOSAL_404.
- POST /proposals/{proposalId}/reject (no body) for the owner who received a
  proposal before payment (PENDING). It marks the proposal REJECTED and keeps
  its likes, which can no longer change. Sending it again returns the same
  result. Errors: PROPOSAL_403_REJECT (not the receiving owner),
  PROPOSAL_409_REJECT (not PENDING), PROPOSAL_409_REJECT_PAYMENT_PENDING (the
  owner is paying), PROPOSAL_404.
- `ProposalStatus` has REJECTED (the owner rejected before payment, or the
  student declined the paid request) and CANCELLED (the student cancelled
  before payment). A REJECTED proposal carries `rejectedBy` (OWNER or
  STUDENT) and `rejectedAt` (Korean time) in the received list, the sent
  list, and the detail; proposals rejected before these fields existed have
  neither.
- Figma: 「받은 제안 상세」 (node 465-230) has 「거절하기」 · 「수락하기」, and
  the received-proposal card on 「내 활동 - 받은 제안 (사장님)」 (node
  2376-3741) has 「자세히 보고 수락하기」 · 「거절하기」. Both open 「제안 거절
  확인 (팝업)」 (node 3546-11584), then 「제안 거절 완료 (팝업)」 (node
  2728-9415). The 사장님 홈 확인할 일 card (node 2328-3480) has one button,
  「자세히 보기」.
- POST /proposals/{id}/payments takes the fee the owner sets (`budget`), so
  `agreement.budget` can differ from `proposedFee`.

## Decision

- **Decline** (`sendWorkDecline` in
  `src/features/student/lib/workStart.ts`): 「거절하기」 shows
  loading dots (ADR 0059) and sends once per press. Success goes to 내 활동 › 보낸 제안. 401 →
  /login; 403 → an alert and `landingPath()`; 404 · 409 → 「지금은 의뢰서를
  거절할 수 없어요…」 above the buttons; anything else → 「잠시 후 다시 시도해
  주세요」.
- **Cancel** (`sendProposalCancel` in
  `src/features/student/lib/sentProposals.ts`): 「제안 취소하기」 shows
  loading dots (ADR 0059) and sends once per press. Success opens 「제안을
  취소했어요」 → 내 활동 › 보낸 제안. Payment pending → 「사장님이 결제하는
  중이라 지금은 취소할 수 없어요」; 404 · 409 → 「이미 수락됐거나 끝난 제안이라
  취소할 수 없어요」 and the detail reloads; 401 → /login; 403 → an alert and
  `landingPath()`.
- **「성사되지 않음」**: a REJECTED or CANCELLED proposal, a proposal whose job
  is CANCELLED, and a CANCELLED job in explore all
  show 「성사되지 않음」, whoever ended it. 내 활동 › 완료 groups them as
  「성사되지 않은 일」 with 「상세보기」, and the detail screen is titled
  「성사되지 않은 작업」. The detail still says who cancelled and what was
  refunded or paid. A rejected proposal shows 「M월 D일 성사되지 않음」
  (`rejectedAt`) in place of 「M월 D일 도착」 · 「M월 D일 보냄」.
- **Reject** (`useProposalReject` and `ProposalRejectDialogs` in
  `src/features/owner`, `sendProposalReject` in
  `src/features/owner/lib/receivedProposals.ts`): a PENDING received
  proposal shows 「자세히 보고 수락하기」 · 「거절하기」 on its 내 활동 card and
  「거절하기」 · 「수락하기」 under its detail. 「거절하기」 opens 「제안을
  거절할까요?」 (「거절하면 되돌릴 수 없어요.」, 「거절하기」 · 「돌아가기」);
  its 「거절하기」 shows loading dots (ADR 0059) and sends once per press. Success
  opens 「학생의 제안을 거절했어요」 (「학생에게는 성사되지 않은 제안으로
  보여요.」) and reloads the screen; 확인 or outside closes it on the card
  and goes back from the detail. Payment pending → 「결제를 진행하는 중이라
  지금은 거절할 수 없어요」; 404 · 409 → 「이미 결제했거나 끝난 제안이라
  거절할 수 없어요」 and the screen reloads; both show under the confirm
  description. 401 → /login; 403 → an alert and `landingPath()`.
- **Home**: the 확인할 일 card for a new proposal has one button, 「자세히
  보기」, to the detail.
- **Fee on the work-start screen**: when `agreement.budget` differs from
  `proposedFee`, the 작업비 row shows 「희망 ○원 → ○원」 with the wished fee
  in grey; otherwise only the fee. `InfoRows` values accept any node.

## Rationale

- One status for every deal that did not go through keeps the lists calm and
  does not single anyone out; the detail screen carries the reason.
- A rejection cannot be undone, so it asks first, like the student's cancel.
- The home card stays one way into the detail, where the owner reads the
  whole proposal before accepting or rejecting.

## Alternatives Considered

- Separate 「취소됨」 · 「거절됨」 chips: rejected, they read as blame and a
  student decline looked like an owner cancel.
- Rejecting with one tap, without a confirm popup: too easy to do by
  mistake for something that cannot be undone.

## Agent Guidance

- When notifications exist, tell the owner that the student declined the
  request; a REJECTED proposal with `rejectedBy` STUDENT (or, for older
  rows without it, with a `jobId`) is a student decline.

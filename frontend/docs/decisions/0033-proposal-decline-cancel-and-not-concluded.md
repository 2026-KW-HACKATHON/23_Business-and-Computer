# 0033. Proposal decline and cancel call the APIs, and every cancelled deal reads 「성사되지 않음」

## Status

Accepted. 「이 조건은 어려워요」 → 「거절하기」 on the work-start screen and
「제안 취소하기」 on the sent-proposal detail call the backend. Every status
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
- `ProposalStatus` has REJECTED (the student declined the paid request) and
  CANCELLED (the student cancelled before payment).
- POST /proposals/{id}/payments takes the fee the owner sets (`budget`), so
  `agreement.budget` can differ from `proposedFee`.

## Decision

- **Decline** (`sendWorkDecline` in
  `src/features/student/lib/workStart.ts`): 「거절하기」 shows 「거절하는
  중...」 and sends once per press. Success goes to 내 활동 › 보낸 제안. 401 →
  /login; 403 → an alert and `landingPath()`; 404 · 409 → 「지금은 의뢰서를
  거절할 수 없어요…」 above the buttons; anything else → 「잠시 후 다시 시도해
  주세요」.
- **Cancel** (`sendProposalCancel` in
  `src/features/student/lib/sentProposals.ts`): 「제안 취소하기」 shows
  「취소하는 중...」 and sends once per press. Success opens 「제안을
  취소했어요」 → 내 활동 › 보낸 제안. Payment pending → 「사장님이 결제하는
  중이라 지금은 취소할 수 없어요」; 404 · 409 → 「이미 수락됐거나 끝난 제안이라
  취소할 수 없어요」 and the detail reloads; 401 → /login; 403 → an alert and
  `landingPath()`.
- **「성사되지 않음」**: a REJECTED or CANCELLED proposal, a proposal whose job
  is CANCELLED, a CANCELLED job in explore, and a cancelled sample work all
  show 「성사되지 않음」, whoever ended it. 내 활동 › 완료 groups them as
  「성사되지 않은 일」 with 「상세보기」, and the detail screen is titled
  「성사되지 않은 작업」. The detail still says who cancelled and what was
  refunded or paid.
- **Fee on the work-start screen**: when `agreement.budget` differs from
  `proposedFee`, the 작업비 row shows 「희망 ○원 → ○원」 with the wished fee
  in grey; otherwise only the fee. `InfoRows` values accept any node.

## Rationale

- One status for every deal that did not go through keeps the lists calm and
  does not single anyone out; the detail screen carries the reason.

## Alternatives Considered

- Separate 「취소됨」 · 「거절됨」 chips: rejected, they read as blame and a
  student decline looked like an owner cancel.

## Agent Guidance

- When notifications exist, tell the owner that the student declined the
  request; a REJECTED proposal with a `jobId` is a student decline.

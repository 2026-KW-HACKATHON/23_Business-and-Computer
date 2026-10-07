# 0037. Choosing an applicant and paying for a request through KakaoPay

## Status

Accepted. 「이 학생에게 맡기기」 (`OwnerAssignPage`) and 안전결제
(`OwnerPayPage`) read the backend and pay through the KakaoPay flow of
ADR 0031. Continues ADR 0030, whose applicant list and applicant profile
open /owner/requests/:requestId/assign/:applicationId. Replaces the sample
checkout and the mock payment of ADR 0014.

## Context

The backend (dev) has:

- POST /jobs/{jobId}/payments `{ jobApplicationId, refundPolicyAgreed: true }`
  → `{ orderId, amount, orderName, nextRedirectPcUrl, nextRedirectMobileUrl }`.
  - The amount is the job's `budget`; the screen cannot change it. The order
    name is the job title.
  - Preparing again replaces the earlier pending order of that job.
  - Errors: 401; 400 COMMON_400; 403 PAYMENT_403_OWNER / OWNER_403; 404
    JOB_404 (no such job, or another owner's); 404 JOB_APPLICATION_404 (no
    such application, or one of another job); 409 PAYMENT_409_UNAVAILABLE
    (the job is not OPEN, the application is not PENDING, or the budget is
    0 or less); 409 PAYMENT_409_PAID; 502 PAYMENT_502_READY.
- POST /payments/{orderId}/approve for a request order moves the job from
  OPEN to MATCHED with that student, accepts the application, and creates
  the chat room in one transaction. Work starts then; there is no student
  start step. The answer is `{ orderId, status, amount, approvedAt, jobId,
  jobStatus: "MATCHED" }` with no chat room id. The other applications stay
  PENDING.
- GET /jobs/{jobId}/applications returns the job (`title`, `budget`,
  `draftDeadline`, `finalDeadline`) and its pending applicants; it has no
  revision count. GET /me/jobs?status=OPEN has `revisionCount`. The
  applicant profile has `proposalCount` and `penaltyCount`.

## Decision

- **Routes**: /owner/requests/:requestId/assign/:applicationId (이 학생에게
  맡기기) and /owner/requests/:requestId/assign/:applicationId/pay (안전결제).
  Both ids are positive numbers; anything else is 「없음」.
- **Data** (`useJobAssignment` in `src/features/owner/hooks/useJobAssignment.ts`):
  the job and the applicant come from GET /jobs/{id}/applications, picked
  by the application id; an id not in the list is not found, and a job that
  is not OPEN is closed. The revision count comes from
  GET /me/jobs?status=OPEN; if that list fails, the 수정 row is left out.
- **이 학생에게 맡기기**: the layout of the Figma screen, with the text set
  to what the backend does: the intro is the title alone (nothing is sent to
  the other applicants), and the second 「맡기면 이렇게 진행돼요」 step reads
  「결제가 끝나면 바로 작업이 시작되고 채팅방이 열려요」. The student line is
  `studentTitle(name)` with 「24학번 · 전공」 and 「★ 4.8 · 완료 3건」 or 「첫
  작업이에요」. The 제안 · 패널티 chips come from the applicant profile and
  are hidden until it loads. Loading and errors use `LoadNotice` (「지원자를
  불러오는 중이에요」 · 「지원자를 불러오지 못했어요」 with 「다시 시도」); a
  closed job shows 「모집이 끝나 학생을 고를 수 없어요」. 「네, 맡길게요」 opens
  the pay route.
- **안전결제**: the same data, with 「결제할 의뢰를 불러오는 중이에요」 ·
  「결제할 의뢰를 불러오지 못했어요」 and 「모집이 끝나 결제할 수 없어요」. The
  method list shows KakaoPay only. 「{작업비} 안전결제하기」 needs the refund
  agreement and sends once per press; it shows 「카카오페이로 이동하고
  있어요」, calls `startKakaoPay({ kind: "job", jobId, jobApplicationId },
  () => prepareJobPayment(…))`, and moves the browser to KakaoPay. A page
  restored from the back-forward cache drops the redirect screen.
- **Prepare failures** (`prepareFailureExit` in `OwnerPayPage`):
  - 401 → /login;
  - PAYMENT_403_OWNER / OWNER_403 → 「사장님만 결제할 수 있어요」, then
    `landingPath()`;
  - JOB_404 → 「의뢰를 찾을 수 없어요」, then 내 활동 › 보낸 의뢰;
  - JOB_APPLICATION_404 → 「지원서를 찾을 수 없어요. 지원자 목록에서 다시
    골라 주세요」, then the applicant list;
  - PAYMENT_409_UNAVAILABLE → 「지금은 결제할 수 없어요. 모집이 끝났거나 이
    학생의 지원이 대기 중이 아니에요」, then the applicant list;
  - PAYMENT_409_PAID → 「이미 결제된 의뢰예요」, then 내 활동 › 진행 중;
  - 502, network, anything else → the payment failure popup, whose
    「다시 결제하기」 returns to the form.
- **Return from KakaoPay** (`KakaoPayResultPage`, ADR 0031): the stored
  target keeps `jobApplicationId`, so 「다시 결제하기」 goes back to the pay
  route for the same applicant. The completion popup reads 「작업비는
  골목인턴이 보관해요. 학생과 채팅으로 자세한 내용을 나눠 보세요.」 and 「확인」
  goes home; the applicant, assign, and pay screens leave the history (ADR
  0047).
- **features/payment**: `prepareJobPayment(jobId, { jobApplicationId,
  refundPolicyAgreed })`; `paymentFailureOf` maps JOB_APPLICATION_404 to
  `applicationNotFound`.
- **Payment phase**: `PaymentPhase` (idle · redirecting · success · failed)
  is in `src/features/owner/types.ts` and exported from
  `src/features/owner/index.ts`; `PaymentProgress`, `OwnerPayPage`, and
  `OwnerProposalAcceptPage` use it.

## Rationale

- One prepare-redirect-approve path for proposal and request payments keeps
  the return screen and the error wording in one place.
- Keeping the application id in the URL and in the stored target lets a
  failed payment be retried for the same student without the list.

## Alternatives Considered

- 「다시 결제하기」 back to the applicant list: rejected, the owner would have
  to find and choose the same student again.
- Opening the chat room after payment: rejected, the approval answer has no
  chat room id.

## Agent Guidance

- Other applications stay PENDING after payment; this screen does not
  mention them.
- The assign route is opened only from the applicant list and the applicant
  profile (ADR 0030), both with the job id and the job application id.

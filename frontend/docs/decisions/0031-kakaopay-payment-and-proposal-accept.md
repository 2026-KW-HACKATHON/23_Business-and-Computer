# 0031. KakaoPay payment flow and paying for an accepted proposal

## Status

Accepted. Implements ADR 0004 for proposal payments and replaces its
「removes `pg_token` from the visible URL」 rule (see Decision). Replaces
the mock payment of the proposal accept screen (ADR 0014, 0025). The
request pay screen (`OwnerPayPage`) uses the same flow (ADR 0037).

## Context

The backend (dev) pays through the KakaoPay test merchant:

- `POST /proposals/{id}/payments` takes the following body:
  - `budget` (Long, required, > 0) — the fee the owner sets;
  - `revisionCount` (Integer, required, ≥ 0);
  - `messageToStudent` (≤ 5000; blank is stored as null);
  - `refundPolicyAgreed` (must be `true`).
  - The fee is that `budget`, and the order name is the proposal title.
  - It returns `{ orderId, amount, orderName, nextRedirectPcUrl,
    nextRedirectMobileUrl }`.
  - Preparing again replaces the earlier pending order of that proposal,
    unless KakaoPay already paid it (409 `PAYMENT_409_PAID`, recovered on
    the server).
- `POST /jobs/{jobId}/payments` returns the same response shape for
  request payments.
- KakaoPay returns to the frontend at /payments/kakao/approval, cancel, or
  fail with `?orderId=…`, under the backend's `KAKAO_PAY_FRONTEND_BASE_URL`.
  Approval adds `&pg_token=…`.
- `POST /payments/{orderId}/approve` `{ pgToken }` returns
  `{ orderId, status: "PAID", amount, approvedAt, jobId, jobStatus }`. It has
  no proposal id. Approving an already paid order returns the stored result.
  The server has no cancel or fail endpoint; such an order stays pending.
- Error codes:
  - Prepare: 401, 400 `COMMON_400`, 403 `PAYMENT_403_OWNER` / `OWNER_403`,
    404 `PROPOSAL_404`, 403 `PROPOSAL_403_PAYMENT` (another store's
    proposal), 409 `PROPOSAL_409_PAYMENT` (not PENDING), 409
    `PAYMENT_409_PAID`, 502 `PAYMENT_502_READY`.
  - Approve: 403 `PAYMENT_403_FORBIDDEN`, 404 `PAYMENT_404_ORDER`, 409
    `PAYMENT_409_UNAVAILABLE`, 409 `PAYMENT_409_PAID`, 502
    `PAYMENT_502_APPROVAL`, 502 `PAYMENT_502_MISMATCH`.
- The proposal detail does not say whose store received the proposal, so
  the accept screen can open for another store's proposal.

## Decision

- **Shared payment feature** `src/features/payment` (no UI):
  - `prepareProposalPayment` and `approvePayment` are in
    `src/features/payment/api/paymentApi.ts` and use `apiData`.
  - `startKakaoPay(target, prepare)` runs the given prepare request and
    stores `{ orderId, target }` in `sessionStorage`
    (`gakkum.pendingPayment`). It returns the KakaoPay URL for this device.
    - `target` is `{ kind: "proposal", proposalId }` or
      `{ kind: "job", jobId }`, so a request payment passes its own
      `POST /jobs/{jobId}/payments` call to the same function.
  - `readPendingPayment(orderId)` returns the stored value only for the same
    order. `clearPendingPayment` removes it.
  - `useKakaoPayApproval(orderId, pgToken)` approves once per order and
    drops late responses.
  - `paymentFailureOf` turns error codes into reasons used by both screens.
- **Device**: the mobile URL is used when any of these is true; otherwise
  the PC URL (QR) is used.
  - `navigator.userAgentData.mobile` is true.
  - The user agent contains Android, iPhone, iPad, iPod, or Mobile.
  - The device looks like a Mac but has touch points (iPadOS).
- **UI**: the screens reuse the owner `PaymentProgress` (Figma 「카카오페이
  이동 중」, 「결제 완료 팝업」, 「결제 실패 팝업」). New optional props are
  `redirectTitle`, `redirectDescription` (one line that replaces the default
  description and the bottom note), `successDescription`, and an optional
  `onCancel`. Without `successDescription` the completion popup reads
  「작업비는 골목인턴이 보관해요. 학생과 채팅으로 자세한 내용을 나눠 보세요.」.
- **Accept screen** (`OwnerProposalAcceptPage`):
  - The method list shows KakaoPay only (`PaymentSection` `methods`).
  - 「안전결제하기」 shows 「카카오페이로 이동하고 있어요」, prepares the
    payment, and moves the browser to KakaoPay. An `inFlight` ref sends one
    prepare per press.
  - When the browser restores the page from its back/forward cache, the
    loading screen is cleared.
  - Before preparing, the press stores the 작업비, 수정 횟수, 한마디, and the
    agreement in `sessionStorage` (`gakkum.proposalAcceptDraft`, one
    proposal at a time). The form opens with them for the same proposal, so
    「다시 결제하기」 from the return page or a reload after KakaoPay keeps
    what the owner chose instead of resetting to the student's fee and 1회.
  - Prepare failures:
    - `PROPOSAL_403_PAYMENT` → alert 「우리 가게가 받은 제안만 결제할 수
      있어요」, then 받은 제안 tab (history replaced).
    - `PROPOSAL_409_PAYMENT` → 「이미 결정한 제안이에요」, then the detail.
    - `PAYMENT_409_PAID` → 「이미 결제된 제안이에요」, then the detail.
    - `PAYMENT_403_OWNER` / `OWNER_403` → 「사장님만 결제할 수 있어요」,
      then `landingPath()`.
    - `PROPOSAL_404` → 「제안을 찾을 수 없어요」, then 받은 제안 tab.
    - 401 → /login.
    - 502, other 400, and network errors → the failure popup; 「다시
      결제하기」 stays on the form.
- **Return page** /payments/kakao/:outcome (`KakaoPayResultPage`). The role
  guard treats paths under /payments as owner-only.
  - **approval**:
    - It shows the loading screen titled 「결제를 확인하고 있어요」 with the
      single line 「잠시만 기다려 주세요. 이 화면을 닫지 말아 주세요.」,
      approves, and then shows the success popup.
    - For a proposal the text is 「작업비는 골목인턴이 보관해요. 학생이 작업을
      시작하면 알려 드릴게요」, and 「확인」 goes home; the proposal detail
      and accept screens leave the history (ADR 0047).
    - `pg_token` stays in the URL, so a refresh approves again and gets the
      stored result.
  - **cancel / fail**: the failure popup is shown. 「다시 결제하기」 goes to
    that proposal's accept screen.
  - **Approval failures**:
    - `PAYMENT_409_PAID` → 「이미 결제된 제안이에요」, then home (ADR 0047).
    - `PAYMENT_403_FORBIDDEN` → 「다른 계정에서 진행한 결제예요. 결제한
      사장님 계정으로 확인해 주세요」, then 받은 제안 tab.
    - `PAYMENT_404_ORDER` → 「결제 정보를 찾을 수 없어요. 다시 결제해
      주세요」, then the accept screen.
    - `PAYMENT_409_UNAVAILABLE` → 「결제를 마무리할 수 없는 상태예요. 내
      활동에서 상태를 확인해 주세요」, then the detail.
    - 401 → /login.
    - 502, network errors, or a missing `pg_token` → the failure popup.
  - **No stored record**, or one for another order:
    - An approval URL with `orderId` and `pg_token` is still approved, then
      「확인」 goes home.
    - Any other return goes straight to 내 활동 › 진행 중.
  - The stored record is cleared when the page is left through a popup
    button or a redirect.
- **Request targets** (`kind: "job"`, with `jobId` and `jobApplicationId`)
  are handled by the same page (ADR 0037):
  - completion goes to 내 활동 › 진행 중, with 「학생과 채팅으로 자세한
    내용을 나눠 보세요」;
  - 「다시 결제하기」 goes to
    /owner/requests/{jobId}/assign/{jobApplicationId}/pay.
- 수정 횟수 starts at 1 and cannot go below 1 on screen, although the server
  accepts 0.

## Rationale

- One prepare-redirect-approve path keeps proposal and request payments on
  the same storage, device choice, and error mapping.
- Leaving the page for KakaoPay means the accept screen is not mounted when
  the proposal turns AWAITING_START, so it never redirects to the detail
  mid-payment; the result page shows completion.
- Approving without the stored record avoids an order that KakaoPay charged
  but the server never recorded.
- Keeping `pg_token` in the URL lets a refresh finish the same approval;
  the token works only for that order and is used only by the server.

## Alternatives Considered

- Asking the backend to return `proposalId` from approval: not chosen; the
  stored record answers it without a backend change.
- `localStorage` for the record: not chosen; `sessionStorage` keeps one
  payment per tab and is cleared with the tab.
- Showing card and bank transfer on the accept screen: rejected; the backend
  pays only through KakaoPay.

## Agent Guidance

- New payment screens call `startKakaoPay` with their own prepare request
  and target, and rely on `KakaoPayResultPage` for the return.
- `KAKAO_PAY_FRONTEND_BASE_URL` on the backend decides which frontend
  KakaoPay returns to. The stored record exists only in the tab that started
  the payment.

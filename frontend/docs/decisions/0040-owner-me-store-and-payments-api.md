# 0040. Owner 내 정보, 가게 정보 수정, and 결제 내역 call the backend

## Status

Accepted. 내 정보 (store details, the four counts, and changing the photo),
가게 정보 수정, 결제 내역, and the payment summary above 내 활동 › 완료 read
and write the backend. The sample store, the demo store state, and the
sample payments are gone. Updates ADR 0017 and ADR 0036.

## Context

The backend (dev) has:

- GET /owners/me → `{ ownerProfileId, profileImageUrl, name, storeName,
  storeAddress, categoryId, description, sentJobCount,
  receivedProposalCount, inProgressJobCount, completedJobCount,
  representativeName, businessNumber }`. `businessNumber` is ten digits
  without dashes; either may be missing.
- PUT /owners/me with `{ storeName (required, ≤ 255), categoryId (required),
  profileImageUrl (http(s) URL or empty), storeAddress (≤ 255), description
  (≤ 5000) }`. Every field is overwritten, so a missing one is cleared. An unknown
  category is CATEGORY_400; a non-owner gets 403.
- GET /business-categories → `[{ id, name }]`; the names match the 11 store
  category chips used at signup.
- GET /payments → `{ summary { thisMonthPaymentAmount, heldAmount,
  totalSettledAmount }, months [{ yearMonth, payments [{ jobId, title,
  amount, refundAmount, approvedAt, studentName, status, settledDate,
  refundedDate }] }] }`, status HELD · SETTLED · PARTIALLY_REFUNDED ·
  FULLY_REFUNDED. `approvedAt` is Korean time (+09:00); `settledDate`
  (SETTLED) and `refundedDate` (refunds) are Korean dates.

## Decision

- **내 정보** (`src/pages/OwnerMePage.tsx`, `useOwnerMe` in
  `src/features/owner/hooks/useOwnerMe.ts`): store name, owner name
  (`ownerTitle` keeps 「데모 사장님」 from doubling), address, photo,
  「사업자 인증 완료」, and the 받은 제안 · 진행 중 · 완료 counts come from GET
  /owners/me. 보낸 의뢰 counts GET /me/jobs?status=OPEN like 내 활동 (ADR
  0042).
  While loading or after a failure the store part shows `LoadNotice` and the counts show loading dots (ADR 0059). Choosing a photo checks its format and size as in signup,
  uploads it as PROFILE, and saves it with PUT /owners/me with the current
  values; the new photo shows while uploading, and a failure shows an alert.
- **가게 정보 수정** (`src/pages/OwnerStoreEditPage.tsx`,
  `src/features/owner/lib/ownerMe.ts`): the fields come from GET /owners/me
  and the selected chip from GET /business-categories. The server has one
  address field, so the address fills 「가게 주소」, 「상세 주소」 starts empty,
  and saving joins them with a space. 「저장하기」 needs a store name, an
  address, and a category; a new photo is uploaded first. the button shows loading dots (ADR 0059), then back to 내
  정보; a failure shows above the button. The locked business
  box shows 「대표자 이새빛 · 사업자번호 123-45-67890」 (`businessInfoText`, the
  number with dashes); with neither value it shows 「사업자 인증 완료」.
- **결제 내역** (`src/pages/OwnerPaymentsPage.tsx`,
  `src/features/owner/lib/paymentHistory.ts`): the summary, then the months
  in server order. Rows read 「학생 · M월 D일」 (보관 중, the Korean date of
  `approvedAt`), 「학생 · M월 D일 정산」 (정산 완료, `settledDate`), 「학생 ·
  M월 D일 작업 중 취소 · N원 환불」 (부분 환불, `refundedDate`), and 「학생 ·
  M월 D일 · N원 환불」 (전액 환불). A row opens 내 활동 › 진행 중, 결과물 보기, or 성사되지 않은
  작업. No payments → 「아직 결제한 의뢰가 없어요」.
- **내 활동 › 완료 summary**: `loadOwnerClosedJobs` sends GET /payments
  together with the closed list and also returns its summary; the box hides
  when the payments fail.

## Rationale

- The counts in GET /owners/me replace the received-proposal list request
  on 내 정보.
- Reusing the closed list's GET /payments keeps 내 활동 at one payment
  request.

## Alternatives Considered

- Splitting the saved address into 가게 주소 and 상세 주소: rejected, an
  address has no fixed split point.

## Agent Guidance

- When GET /payments says a job completed automatically, show 「M월 D일 자동
  완료 정산」 as in Figma.

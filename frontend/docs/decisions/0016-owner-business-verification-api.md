# 0016. Owner business verification calls the backend API

## Status

Accepted

## Context

ADR 0010 shipped owner signup step 2 (사장님 인증) with a local MOCK check.
The backend endpoint `POST /auth/owner-verification/business` is ready:

- Auth: `Authorization: Bearer {accessToken}` of a PENDING (가입 대기) user.
- Body: `representativeName` (not blank, max 255, trimmed by the server),
  `openedAt` (`LocalDate`, so ISO `YYYY-MM-DD` only, not in the future), and
  `businessNumber` (backend regex: 10 digits as 3-2-5, hyphens optional).
- 200 `{ "success": true, "data": { "verified": true | false } }`. A business
  that does not match the 국세청 record is `verified: false`, not an error.
- Errors use `{ "success": false, "error": { "code", "message" } }`: 400
  `COMMON_400` (validation or parse), 401 `COMMON_401`, 409
  `USER_409_REGISTERED` (already signed up), 503 `OWNER_BUSINESS_503` (국세청
  call failed), 500 `COMMON_500`.

The Notion spec does not state the `openedAt` format; the screen keeps it as
8 digits (`YYYYMMDD`).

Figma only has 인증 전 / 인증 완료 / 정보 불일치, so requesting and server
failures needed a decision.

## Decision

- `checkBusinessInfo` in `src/features/signup/lib/businessInfo.ts` is now async.
  It still checks the format locally first (same regex and date rules as
  before) and returns 정보 불일치 without a request when that fails. Otherwise
  it calls `verifyOwnerBusiness` in `src/features/signup/api/signupApi.ts`,
  which converts `openedAt` to `YYYY-MM-DD`, trims the name, sends the number
  as typed, and adds the stored access token as `Authorization: Bearer`.
- Outcome mapping on the screen:
  - `verified: true` → 인증 완료.
  - `verified: false`, or 400 after the local check passed → 정보 불일치
    (red borders + 「사업자 정보가 일치하지 않아요」).
  - 503, 500, or a network error → `error` state: no red borders,
    「잠시 후 다시 시도해 주세요」 in the same spot, 「인증하기」 enabled again.
  - 401 → /login (history replaced).
  - 409 → alert 「이미 가입을 마친 계정이에요」, then the owner home /owner
    (replaced).
  - While requesting, the button is disabled and reads 「인증 중...」.
- The shared signup draft stores only the result in `business.check`
  (idle / verified / mismatch / error). "Requesting" is local `useState` in
  `src/pages/OwnerSignupVerifyPage.tsx`, so it never outlives the screen.
- Editing a field or leaving the screen while a request is in flight discards
  its late response.
- `apiFetch` in `src/api/client.ts` now merges headers as
  `{ ...init, headers: { "Content-Type": "application/json", ...init.headers } }`
  so a caller's headers add to the JSON content type instead of replacing it,
  and `ApiError` carries the backend `error.code` when the body has one.

## Rationale

- The local format check matches the backend validation, so a 400 after it
  passes is rare (e.g. server-side date differences) and is closest to a
  mismatch from the user's view.
- Temporary failures should not look like the user typed something wrong, so
  they keep the borders neutral and allow a retry.

## Alternatives Considered

- Keeping the MOCK as a fallback when the API fails: rejected, it would let an
  unverified business continue.
- Changing the shared `TextField` to show an error text without `invalid`:
  rejected for this change, the page renders its own message instead.
- Keeping a `checking` value in the draft's `business.check`: rejected. The
  draft is shared by steps 1–3, and because a late response is discarded after
  the screen is left, `checking` stayed behind and the button was still
  disabled after going back and returning (PR #101 review).

## Agent Guidance

- Still to do from ADR 0010: photo upload and `POST /auth/owner` on step 3.
  The verification result is not stored on the server, so the final signup
  request must send the business info again.
- Calls that need auth pass `Authorization` through `apiFetch`'s `init.headers`
  as a plain object; a `Headers` instance would not be spread.

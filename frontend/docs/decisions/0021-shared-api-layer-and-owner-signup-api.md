# 0021. Shared API layer, access-token-only storage, and owner signup API

## Status

Accepted. Replaces the local-only 「회원가입 완료」 of owner signup step 3
(ADR 0010) and the token pair storage of ADR 0003.

## Context

Every screen is about to call the backend (의뢰 · 제안 · 작업), but each
feature still added its own `Authorization` header, read the
`{ success, data }` envelope by hand, and nothing handled an expired token.
The shared layer `src/api` may not import from `features`
(`docs/conventions/directory-structure.md`), while the token storage lived in
the auth feature.

Backend facts (dev):

- `POST /jwt/exchange` (after Kakao login) and `POST /refresh` both read the
  HTTP-only `refreshToken` cookie, rotate it, and return a raw
  `{ accessToken }` with no envelope. Signup calls return the same token
  shape inside the envelope. No call returns a refresh token in the body, so
  the old `saveTokens` stored the string `"undefined"` as the refresh token.
- An expired or invalid access token gets a raw 401
  `{ "error": "…" }`; other 401s use the envelope with `COMMON_401`.
- `POST /media/images/uploads` takes `purpose` `PROFILE` · `STORE` ·
  `PROPOSAL` (jpg/png/webp, up to 10 MB).
- `POST /auth/owner` body: `name`, `storeName`, `storeAddress?`,
  `categoryId` (business category **id**), `businessNumber` (3-2-5 digits,
  hyphens optional), `openedAt` (`YYYY-MM-DD`), `representativeName`,
  `description?`, `storeImageUrls` (up to 5), `profileImageUrl?`. 200
  `{ data: { accessToken } }` with role OWNER; refresh token only as the
  cookie. Errors: 409 `USER_409_REGISTERED`, 409
  `OWNER_409_BUSINESS_NUMBER`, 400 `CATEGORY_400`, 400 `COMMON_400`,
  409 `COMMON_409`, 401.
- Business categories have a table but no list endpoint and no seed
  migration. The backend teammate filled the table; `GET
  /business-categories` was requested and is not deployed yet (the deployed
  server answers it with the same 500 `COMMON_500` as an unknown path).

## Decision

- `src/api/tokens.ts` owns token storage (`saveAccessToken`,
  `getAccessToken`, `clearTokens`, `isLoggedIn`). Only the access token is
  stored; a leftover `refreshToken` key is removed. The auth feature
  re-exports these, so pages keep importing from `src/features/auth`.
  `CookiePage` stores the access token from `exchangeCookieForAccessToken`.
- `src/api/client.ts` adds `authHeaders()` and `apiData<T>(path, init)`:
  it attaches the stored token, returns the envelope's `data`, and on 401
  calls `POST /refresh` once (shared by parallel calls), stores the new
  token, and retries. If the refresh fails it clears the token and rethrows
  the 401 `ApiError`; pages still send the user to /login. `apiFetch` stays
  the raw call for endpoints without the envelope.
- `src/api/media.ts` `uploadImage(file, purpose)` is the one image upload
  for every feature. Signup's `uploadProfileImage` and the new
  `uploadStoreImage` call it.
- Owner signup step 3 (`src/pages/OwnerSignupProfilePage.tsx`,
  `src/features/signup/lib/ownerRegistration.ts`) follows the student signup
  rules of ADR 0019: find the category id by the chip name in
  `GET /business-categories`, upload the profile and store photos (each
  photo once, reused on retry), then `POST /auth/owner`; on success the new
  token is saved and the done screen opens. While sending, the button shows
  loading dots (ADR 0059) and inputs are locked; a ref blocks double submits.
  Failures show one line above the button: category lookup or
  `CATEGORY_400` → 「업종 정보를 불러오지 못했어요…」,
  `OWNER_409_BUSINESS_NUMBER` → 「이미 다른 계정에서 가입한 사업자등록번호예요」,
  photo upload → 「사진을 올리지 못했어요…」, other 400 / 409 / 5xx as in
  ADR 0019. `USER_409_REGISTERED` alerts and goes to `landingPath()`; 401
  goes to /login. Photos of the wrong type or size are rejected when picked.

## Rationale

- One client keeps the auth header, envelope, and refresh in a single place
  for the request and proposal work that follows.
- Storing only the access token matches what the backend actually sends and
  removes the `"undefined"` refresh token.
- Matching categories by name keeps the screen's 11 chips while the server
  owns the ids, the same way specialties work (ADR 0019).

## Alternatives Considered

- A token provider injected into `src/api` at startup: rejected, moving the
  small storage module is simpler and keeps the layer rule.
- Hardcoding category ids read from the database: rejected, ids would break
  if the table is refilled.
- Signing up without photos when an upload fails: rejected, same as the
  student signup.

## Agent Guidance

- Use `apiData` for any authenticated endpoint with the envelope, and
  `uploadImage` for images (`PROPOSAL` for proposal photos).
- Owner signup needs `GET /business-categories` (`[{ id, name }]`) on the
  server; until it is deployed, step 3 stops with the category message.
- The demo login for 둘러보기 (ADR 0024) returns tokens the same way
  (access token in the body, refresh token as the cookie), so `apiData`'s
  refresh path serves it too.

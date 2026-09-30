# 0010. Owner signup screens before backend integration

## Status

Accepted

## Context

Figma 「1. 공통」 has the owner signup: 1/3 가게 정보 입력, 2/3 사장님 인증
(states 인증 전 / 인증 완료 / 오류 · 정보 불일치 / 오류 · 휴업·폐업), 3/3 프로필
입력, and 회원가입 완료. The Notion 「화면 상태 전환표」 fixes the routes
/signup/owner/1, /2, /3, /done and says the hackathon business check is a MOCK
that only checks the format.

The backend already has `POST /auth/owner-verification/business`,
`POST /media/images/uploads`, and `POST /auth/owner`, but the team chose to
ship the screens first and wire the API in a later change.

## Decision

- /signup/owner is a layout route (`src/pages/OwnerSignupLayout.tsx`) that
  wraps the four steps in `OwnerSignupProvider` from `src/features/signup`.
  Input lives in memory only; a reload or a direct visit to a later step
  sends the user back to /signup/role, as the Notion rule 「가입 중간에 앱을
  닫으면 다시 역할 선택부터」 says. /signup/owner redirects to step 1.
- Step 1 enables 「다음」 when 이름, 업종 (one of 11), 가게 이름, 매장 주소 are
  filled and the terms box is checked. 「보기」 opens `TermsSheet`.
- Step 2 checks the business info locally with `checkBusinessInfo`: a valid
  business number (backend regex), a real 8-digit opening date not in the
  future, and a non-empty representative name give 인증 완료; anything else
  gives 정보 불일치. The business number is formatted as 3-2-5 while typing.
  Editing any field returns to 인증 전.
- Step 3 keeps the profile photo and up to 5 store photos as `File`s with
  local previews; 「회원가입 완료」 goes to /signup/owner/done (history
  replaced) without saving anything yet.
- 업종 values are the Korean names in `src/types/storeCategory.ts`.

## Rationale

- Screens first lets design review happen while the API contract is settled.
- Keeping the draft in memory matches the Notion reset rule and avoids storing
  business numbers in the browser.

## Alternatives Considered

- One route with a step state: rejected, the Notion table gives each step its
  own route.
- Persisting the draft in `sessionStorage`: rejected for the reason above.

## Agent Guidance

- Backend integration still to do: call the business verification API in
  step 2, upload photos through `POST /media/images/uploads`, then `POST /auth/owner`
  on 「회원가입 완료」 and store the returned tokens. Requests need the stored
  access token as `Authorization: Bearer`, which `apiFetch` does not add yet.
- `POST /auth/owner` takes `categoryId`, but the backend has no 업종 seed data
  or list API yet; agree on the id mapping with the backend before wiring it.
- The backend check only returns `verified: true/false`, so the 오류 · 휴업·폐업
  state (`check === "closed"`) is rendered but not reachable until the backend
  reports business status.

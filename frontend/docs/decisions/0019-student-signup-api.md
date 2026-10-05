# 0019. Student signup calls the backend API

## Status

Accepted. Replaces the MOCK verification and the local-only 「회원가입 완료」
in ADR 0011.

## Context

ADR 0011 shipped student signup steps 1–3 with a MOCK code (`123456`) and no
save. The backend (dev) is ready, and differs from the Notion spec:

- Every call needs `Authorization: Bearer {accessToken}` of a PENDING user.
- `POST /auth/student-verification/email` `{ email }` sends a 6-digit code,
  valid **10 minutes**. 200 `{ "success": true }`. Errors: 401 `COMMON_401`,
  409 `USER_409_REGISTERED`, 409 `USER_409_EMAIL` (mail used by another
  account), 429 `STUDENT_EMAIL_429` (sent less than 60 s ago — counted **per
  user**, not per address), 503 `STUDENT_EMAIL_503` (mail not sent),
  400 `COMMON_400`.
- `POST /auth/student-verification/email/verify` `{ email, code }`. A wrong,
  expired, or 6th+ attempt all return the same 400 `STUDENT_EMAIL_400`; after
  5 wrong codes even the right one fails until a new code is sent. A new code
  also clears an earlier 인증 완료. A verified mail must be used for signup
  within 30 minutes.
- `GET /specialties` returns `data` as an array
  `[{ id, name, specialties: [{ id, name }] }]`. The server DB has the
  specialty seed data (added outside the Flyway migrations), so names and ids
  come from the server, not from the frontend.
- `POST /media/images/uploads` `{ purpose: "PROFILE", fileName, contentType,
  size }` → 201 `{ uploadUrl, uploadHeaders, uploadUrlExpiresAt, imageUrl }`.
  The browser `PUT`s the file to `uploadUrl` with `uploadHeaders`; `imageUrl`
  is the public address. jpg/png/webp only, up to 10 MB. PENDING users may
  upload.
- `POST /auth/student` (not the Notion `POST /auth/signup/student`) →
  200 `{ data: { accessToken } }` with role STUDENT; the refresh token comes
  only as an HTTP-only cookie. Body rules: `university` exactly 「광운대학교」,
  `studentNumber` 10 digits, `portfolioUrl`/`profileImageUrl` empty or
  starting with http:// or https://, `specialtyIds` and `certificates`
  optional, each certificate needs `certificateName` and `acquiredYear`
  (≥ 1900); there is no `issuingOrganization` (removed from the backend in
  PR #118).
  Errors: 401, 409 `USER_409_REGISTERED`, 409 `USER_409_EMAIL`, 409
  `STUDENT_409_NUMBER`, 400 `SPECIALTY_400` / `SPECIALTY_400_DUPLICATE`,
  403 `STUDENT_EMAIL_403` (not verified, other mail, or over 30 minutes),
  400 `COMMON_400`, 409 `COMMON_409`, 500 `COMMON_500`.

Figma has no requesting, cooldown, loading, empty-list, or server-error
states.

## Decision

- Calls live in `src/features/signup/api/signupApi.ts`. The S3 `PUT` uses
  `fetch` directly there (not `apiFetch`, which adds cookies and a JSON
  content type the signed URL does not expect). `src/features/signup/lib/studentInfo.ts`
  maps errors to result values (`sendVerificationCode`, `verifyCode`,
  `uploadSignupPhoto`, `registerStudentSignup`), as `checkBusinessInfo` does
  for the owner (ADR 0016). Pages keep 「requesting」 in local state and drop
  late responses with a `requestId` ref. A second `inFlight` ref makes a
  fast double click send only one request (send, resend, verify, and
  `POST /auth/student`), even before the disabled button is drawn.
- When input errors show: a format error appears only after the user leaves
  the field (blur), or presses 「다음」 / 「회원가입 완료」. Once shown, it
  disappears as soon as the value is fixed; if the value goes wrong again it
  waits for the next blur. Buttons stay disabled by the same rules as before,
  so in practice the blur shows the error. Server-originated errors (taken
  학번, taken mail) show at once.
- Step 1: 학번 must be 10 digits, else 「학번 10자리를 입력해 주세요」 and
  「다음」 stays disabled. If signup later answers `STUDENT_409_NUMBER`, the
  draft keeps that number in `takenStudentNumber` and step 1 shows
  「이미 가입된 학번이에요」 until the number changes.
- Step 2: send, resend, and verify call the API; buttons read
  「보내는 중...」 / 「확인 중...」 and inputs are read-only while a request runs.
  - Resend countdown: every successful send or resend starts a 60 s
    countdown at once; send and resend stay disabled and a grey line reads
    「N초 뒤에 다시 보낼 수 있어요」. A 429 shows the same text in red; its end
    is 60 s after the last send from this screen, or 60 s from now when
    unknown. The countdown survives 「변경」, because the server limit is per
    user, not per address.
  - Skipping re-verification: a successful verify stores `verifiedEmail` and
    `verifiedAt` in the draft. Coming back to step 2 (e.g. after
    `STUDENT_409_NUMBER` sent the user to step 1) with that mail verified less
    than 30 minutes ago (`isEmailVerificationFresh`, the backend's
    consume window) shows 「인증을 마친 메일이에요」 and a 「다음」 button, with
    no new code. 「변경」 or an expired window (checked again on 「다음」, then
    「메일 인증을 다시 해 주세요」) goes back to normal verification. When step
    3 sends the user back because of `STUDENT_EMAIL_403` or
    `USER_409_EMAIL`, the skip does not apply.
  - 503 → 「메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요」.
  - 409 `USER_409_EMAIL` → red email field 「이미 다른 계정에서 사용 중인
    메일이에요」.
  - 400 on verify → 「인증번호가 일치하지 않아요」 while the screen timer runs,
    「인증 시간이 지났어요. 인증번호를 다시 받아 주세요」 after it ends. The
    screen counts wrong codes; from the 5th it shows 「여러 번 틀렸어요.
    인증번호를 다시 받아 주세요」 and stops sending verify requests.
  - Any successful send clears `verifiedEmail`/`verifiedAt`, the wrong-code
    count, and the code, because the server clears its 인증 완료 too.
  - 401 → /login; 409 `USER_409_REGISTERED` → alert 「이미 가입을 마친
    계정이에요」, then `landingPath()`. Other failures →
    「잠시 후 다시 시도해 주세요」.
- Step 3:
  - Badges come from `GET /specialties` (loading line, or 「다시 시도」 on
    failure). The draft stores `specialtyIds`; 1–5 picks as before.
    `src/types/specialty.ts` is no longer used for signup (other screens
    still use it for sample data).
  - Hidden on this screen: categories with `specialties: []`
    (`selectableCategories`), and categories whose only specialty has the
    category's own name (`implicitSpecialty`). The second rule keeps 「기타」
    hidden in signup even though the backend now has a 「기타」 specialty under
    the 「기타」 category; the extra filter is applied in
    `src/pages/StudentSignupProfilePage.tsx` only, because 제안 보내기 2/4
    shows 「기타」 (ADR 0020). `fetchSpecialties` returns every category
    unchanged so each screen decides. These helpers live in the shared
    `src/features/specialty` (ADR 0020).
  - The 1–5 rule always applies. If no category has a specialty after that
    filter, the panel shows only 「선택할 특기가 아직 없어요」, and
    a failed load shows only 「특기 목록을 불러오지 못했어요」 with 「다시 시도」
    (no count or guide line in either case); 「회원가입 완료」 stays
    disabled.
  - Certificates are 이름 / 취득 연도 (4 digits, 1900–this year) only; there
    is no 발급 기관 field and each item is sent as
    `{ certificateName, acquiredYear }`. A row whose two values are empty or
    only spaces (the first row or one added with 「+ 자격증 추가」) is not
    sent (`isBlankCertificate`). `certificateStatuses` checks the other rows;
    each problem disables 「회원가입 완료」 and shows under its row once focus
    leaves that row (moving between the row's two fields does not count):
    - only one value → 「자격증 이름과 취득 연도를 모두 입력해 주세요」;
    - year not 4 digits in 1900–this year → 「1900~(올해) 사이 연도를
      입력해 주세요」 with the year number;
    - the same name as an earlier row, ignoring spaces and case →
      「같은 자격증이 두 번 입력됐어요」. The backend has no such rule; this is
      a frontend rule.
  - A portfolio value that does not start with http:// or https:// gets
    https:// added when sent.
  - The photo picker accepts jpeg/png/webp; a wrong type or over 10 MB is
    refused with a message. On submit the photo is uploaded first; its
    `imageUrl` is reused if the same file is submitted again. If the upload
    fails, signup stops with 「사진을 올리지 못했어요. 다시 시도해 주세요」.
  - While saving, the button reads 「가입 중...」.
  - Errors: `STUDENT_409_NUMBER` → step 1; `STUDENT_EMAIL_403` → step 2 with
    「메일 인증을 다시 해 주세요」; `USER_409_EMAIL` → step 2 with the taken-mail
    error (both through router state `StudentVerifyReturnState`);
    `SPECIALTY_400*` → clear picks, reload the list, 「특기를 다시 골라 주세요」;
    `USER_409_REGISTERED` → alert, then `landingPath()`; 401 → /login;
    any other 400 (e.g. `COMMON_400`) → 「입력한 내용을 다시 확인해
    주세요」; any other 409 (e.g. `COMMON_409`) → 「일시적인 문제가 생겼어요.
    다시 시도해도 안 되면 문의해 주세요」; 5xx or network →
    「잠시 후 다시 시도해 주세요」.
  - 409 wording rule: a 409 whose cause is clear keeps its own message and
    flow (`STUDENT_409_NUMBER` → step 1 「이미 가입된 학번이에요」,
    `USER_409_EMAIL` → step 2 「이미 다른 계정에서 사용 중인 메일이에요」,
    `USER_409_REGISTERED` → alert). `COMMON_409` is any DB constraint
    failure and can be a server data problem unrelated to the input (a
    lagging identity sequence made one signup fail and the same retry pass),
    so it must not tell the user to fix the input. A plain 400 still asks the
    user to check the input, since it fails again with the same values (see
    `docs/failures/0002-student-signup-common-409.md`).
- On success `saveAccessToken` (auth feature) stores the new access token and
  removes the stored refresh token, which the backend no longer accepts and
  `POST /refresh` never reads (it uses the cookie). This happens inside
  `registerStudentSignup`, so the token is kept even if the screen dropped
  the response. 완료's 「시작하기」 goes to `landingPath()`.
- `ProfilePhoto` takes an optional `accept`, and `ResendButton` an optional
  `disabled`; defaults keep other screens unchanged.

## Rationale

- The server gives one code for wrong and expired codes, so the screen timer
  and a local count are the only way to show the three Figma-like messages.
- Locking inputs during a request, instead of discarding responses on edit,
  avoids throwing away a verify or signup the server already accepted.
- Sending the photo first keeps signup to one request with a ready URL, and
  stopping on upload failure avoids a profile silently missing its photo.

## Alternatives Considered

- Keeping the hardcoded badge names and looking up ids by name: rejected,
  the server owns the specialty data and one differing name would break the
  lookup.
- A 발급 기관 field: rejected, the team dropped it from the product and the
  backend.
- Skipping the 1–5 rule when the list is empty: rejected, it would let
  students sign up with no specialty.
- Signing up without the photo when the upload fails: rejected for now; the
  user asked to stop and retry.

## Agent Guidance

- `landingPath` sends STUDENT to /student; the student home still shows
  sample data (ADR 0018) until its API is wired.
- Owner step 3 (`POST /auth/owner`) is wired in ADR 0021 with the same
  rules; image upload and the auth header now live in `src/api`.

# 0011. Student signup screens before backend integration

## Status

Accepted

## Context

Figma 「1. 공통」 has the student signup: 1/3 정보 입력, 2/3 학생 인증 (광운대
이메일) with states 발송 전 / 발송 후 / 오류 · 메일 도메인 / 오류 · 인증번호,
3/3 프로필 입력, and 회원가입 완료. The Notion 「화면 상태 전환표」 fixes the
routes /signup/student/1, /2, /3, /done: only @kw.ac.kr mail, a 6-digit code
valid for 5 minutes, 1–5 badges, and a certificate row added by 「+ 자격증
추가」. As with the owner signup (ADR 0010), the team ships the screens before
wiring the backend.

## Decision

- /signup/student is a layout route (`src/pages/StudentSignupLayout.tsx`)
  wrapping the steps in `StudentSignupProvider`. Input stays in memory; a
  reload or a direct visit to a later step goes back to /signup/role.
- Step 1 enables 「다음」 when 이름, 학부, 학번 (digits only) are filled and the
  terms box is checked. 대학교 is the locked `UniversityField`.
- Step 2 keeps its state in the page. 「인증번호 발송」 with a non-@kw.ac.kr
  address shows the domain error and disables the button until the address
  changes. A valid address starts a 5-minute timer without sending mail.
  The MOCK code is `123456`; any other code shows 「인증번호가 일치하지 않아요」,
  and an expired timer shows 「시간이 지났어요」. 「변경」 goes back to 발송 전,
  and 「인증번호 재발송」 restarts the timer and turns into 「재발송되었어요.」.
- Step 3 lists the badges from `src/types/specialty.ts` with `Chip`
  (outlined). A sixth pick is refused and the guide line turns red with
  「최대 5개까지 고를 수 있어요」 instead of a toast, because there is no toast
  component yet. 「회원가입 완료」 needs at least one badge.
- The owner and student steps share `src/pages/SignupPage.css` (`.signup`)
  and `src/pages/SignupDonePage.css` (`.signup-done`).

## Rationale

- Keeping the verification state local to step 2 matches the Notion rule that
  its states are one screen, and nothing after step 2 needs the code or timer.
- An inline hint keeps the badge limit visible without adding a toast system
  before the screens that need one exist.

## Alternatives Considered

- Accepting any 6-digit code in the MOCK: rejected, the mismatch state could
  not be tested.
- Adding a toast component now: deferred until more screens need it.

## Agent Guidance

- Backend integration still to do: `POST /auth/student-verification/email`
  on send/resend, `POST /auth/student-verification/email/verify` on 「인증
  완료」, profile photo upload, and the student registration call. Requests
  need `Authorization: Bearer` (see ADR 0010).
- Replace the badge-limit hint with a toast when a shared toast component is
  added.

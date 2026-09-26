# 0006. Login and role selection screens

## Status

Accepted

## Context

Figma 「1. 공통 (온보딩·로그인)」 has three screens for this step: 로그인,
회원가입 - 역할 선택 (카카오 로그인), and 둘러보기 - 역할 선택 (로그인 없이).
The Notion 「화면 상태 전환표」 fixes their routes and moves: Kakao login only,
a demo entry and a Notion link on the login screen, and 「←」 on both role
screens goes back to login.

Only the screens are in scope. Deciding new vs existing user after Kakao login,
demo accounts, and the signup and home screens come later.

## Decision

- /login shows only Kakao login. The button keeps the existing full-page
  redirect to `socialLoginUrl("kakao")` from ADR 0003; the Google and Naver
  buttons are removed.
- 「로그인 없이 둘러보기(Demo)」 and 「노션에서 개발 과정 보기」 are always
  shown. The demo lets presentation viewers enter the app without Kakao login
  as a fixed guest owner or student account with pre-seeded data, so there is
  no flag to hide it for now. The Notion link stays visible permanently.
- /signup/role and /demo/role render one page, `src/pages/RoleSelectPage.tsx`,
  with `mode="signup"` or `mode="demo"`. Only the title, button wording, and
  next routes differ.
- Next routes follow the Notion route table: signup goes to /signup/owner/1 and
  /signup/student/1; demo goes to /owner and /student. Until those screens
  exist, the catch-all route sends them to /.
- The Figma hover variant (character grows from 136px to 160px) becomes a
  press effect, plus hover on devices that support it.

## Rationale

- One page with a mode avoids duplicating an identical layout.
- The hackathon presentation needs the demo entry on the live screen, so a
  hide switch would only add configuration without a current use.

## Alternatives Considered

- Two separate page files: rejected, the layouts are the same.
- Sending the Kakao button straight to /signup/role like the Figma prototype:
  rejected, it would drop the working OAuth2 redirect.
- An env flag (`VITE_DEMO_ENABLED`) to hide the demo entry and Notion link, as
  the Notion route table suggests: deferred. Revisit when preparing a release
  without the demo accounts.

## Agent Guidance

- When signup or home screens are added, register them at the routes above so
  the role buttons start working without changes here.
- Keep the new-vs-existing user decision out of these pages; it belongs to the
  auth feature once the backend contract is settled.

# 0075. Login and intro show request and proposal as two separate starts

## Status

Accepted. The login usage cards and the intro flow now say that work starts
in one of two ways: the owner posts a request, or a student sends a
proposal. Empathy is shown as something added to a proposal. This updates
the login cards from ADR 0007 and the intro flow from ADR 0009.

## Context

- The login screen said 「골목인턴은 이렇게 돌아가요」 above three cards that
  advance on their own: proposal → empathy → request, with page dots. People
  read them as three steps and thought a proposal (and empathy) had to come
  before a request.
- In the service the two are independent. An owner can post a request that
  students apply to, and a student can send a proposal to a store first.
  Empathy only exists on proposals. After either start, payment, work,
  drafts, revisions, and completion are the same.
- The intro flow already said 「제안 또는 의뢰」 in step 1, but as one line of
  text it was easy to miss in three seconds.

## Decision

- **Login title**: 「일이 시작되는 두 가지 방법」 (the logo above already says
  골목인턴).
- **Usage cards** (`UsageCardCarousel`), in this order, each with a label
  above the illustration and one caption line below it:
  1. 「사장님이 먼저 · 의뢰」 (owner yellow), 「사장님이 의뢰를 올리면 학생이
     지원해요」, request illustration;
  2. 「학생이 먼저 · 제안」 (student plum, white text), 「학생이 가게에 먼저
     제안하면 사장님이 골라요」, proposal illustration;
  3. 「제안에 더하기 · 공감」 (light plum, plum text), 「다른 학생들이 좋은
     제안에 공감해요」, empathy illustration.
  The label and caption move with the card. Auto-advance goes from 2 s to
  3 s, so there is time to read the label and caption. Swipe, tap, and the
  dots are unchanged. The illustrations are unchanged.
- **Intro step 1**: two chips, 「사장님 의뢰」 (owner yellow) 「또는」 「학생
  제안」 (student plum), at the same size as the other step labels. Steps
  2–5 (작업 시작 · 초안 · 수정 · 완료) are unchanged, so both starts visibly
  join one flow. Below 360px width the chips shrink so step 1 stays on one
  line.

## Rationale

- Labels that name who starts make each card a separate path instead of a
  numbered step, without new illustrations.
- Putting the request first matches what most owners expect, and putting
  empathy last with 「제안에 더하기」 keeps it attached to proposals.
- Showing the split only in intro step 1 keeps the shared steps after it.

## Alternatives Considered

- **One illustration with two lanes that merge**: clearest, but needs a new
  illustration.
- **Tabs (사장님이 먼저 | 학생이 먼저) instead of the carousel**: shows only
  one path at a time.

## Agent Guidance

- Card copy and colors live in `CARDS` in
  `src/components/UsageCardCarousel/UsageCardCarousel.tsx` and in Figma page 0
  「사용법 카드 넘기기 (시작·로그인 화면용)」. Change them together.
- Intro step 1 is the first item of `FLOW_STEPS` in `src/pages/IntroPage.tsx`;
  Figma page 1 「로그인 전 히어로 / 흐름 차례로 등장」 has the same chips in
  every variant.

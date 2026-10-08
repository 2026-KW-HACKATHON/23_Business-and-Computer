# 0053. First guides over the home

## Status

Accepted. Two guides cover the home once:

- 「가입 후 첫 안내」 right after signing up: the home dims, 확인할 일, the bell,
  and the floating button stay lit, and three numbered tips and a glowing
  「알겠어요」 appear one after another.
- 「둘러보기 첫 안내」 when 둘러보기 starts from the role screen: the home dims
  and blurs, the role switch badge stays lit, a glass card explains the demo,
  a bubble points at the badge, and 「알겠어요」 follows.

## Context

- Figma A-2:
  - 「사장님 홈 - 가입 후 첫 안내」 (node 2774-4221), 「학생 홈 - 가입 후 첫 안내」
    (node 2786-6705), overlay sets 「가입 후 첫 안내 / 말풍선 차례로」 (nodes
    3359-4635 · 3359-7680);
  - 「사장님 홈 - 둘러보기 첫 안내」 (node 2473-4154), 「학생 홈 - 둘러보기 첫
    안내」 (node 2475-1709), overlay set 「둘러보기 첫 안내 / 안내 차례로」 (node
    3359-4704);
  - 「첫 안내 / 알겠어요 (반짝임)」 (node 3354-4591).
- The new account's home is 「처음」 (ADR 0051), so the lit card is the 「처음」
  card.
- The backend keeps no 「가입 후 안내 봄」 value (answer of 10/3); the
  frontend remembers both guides.

## Decision

- **Frame** (`GuideOverlay`, a portal as wide as the app, 390px at most):
  - a dim layer (rgba(12, 12, 12, 0.78), fading in over 0.4s; blurred 6px
    for 둘러보기) drawn as a real element;
  - ✕ at the top right: a 40px solid #3a3a3a circle with a bold 16px ✕. The
    web has no status bar, so it sits over the dimmed 내 정보 icon and must
    not let it show through;
  - 「알겠어요」 (role-color button, 20px from the bottom) rising in at
    `okDelay`, with a role-color glow behind it that spreads and fades every
    second;
  - 알겠어요, ✕, a tap anywhere, or Esc closes it. `prefers-reduced-motion`
    shows everything at once without the glow.
- **Places** (`useGuideMeasure`): what stays lit is drawn again at the place
  of the real element, measured every frame for the first second (the app bar
  badge and the demo strip settle late) and again on resize. The elements
  are the card ref, `[data-guide="bell"]` on the app bar bell,
  `.main-tab-screen__fab`, and `[data-guide="demo-badge"]` on the role switch
  badge.
- **가입 후 첫 안내** (`SignupGuide`, `src/lib/signupGuide.ts`):
  - a successful POST /auth/owner or /auth/student stores the role under
    `signupGuide` in localStorage. The home of that role shows the guide while
    the key matches and the home is 「처음」; closing removes the key, so it
    never comes back on this device. Another device may show it once more;
  - the 「처음」 card is drawn again, not clickable, its button at 40%; the bell
    sits on a 44px white circle; the floating button is drawn at 55%;
  - 「골목인턴에 오신 걸 환영해요」 (24px, white) 14px above the card;
  - tips on `--color-main` pills: ① under the card 「진행 상황은 여기 확인할
    일에서 봐요」, ② left of the floating button 「새 의뢰는 여기서 올려요」 ·
    「새 제안은 여기서 써요」, ③ left of the bell 「학생 제안이 오면 알림으로
    알려 드려요」 · 「사장님 답이 오면 알림으로 알려 드려요」, rising in at 0.5s,
    1s, and 1.5s; 「알겠어요」 at 2s.
- **둘러보기 첫 안내** (`DemoGuide`, `src/lib/demoGuide.ts`):
  - a successful 「사장님으로 · 대학생으로 둘러보기」 on the role screen marks
    `demoGuide` pending in sessionStorage unless this tab has seen it. The home
    shows the guide while pending; closing marks it seen. The role switch
    badge and coming back to the home never show it again in the tab; a new
    tab shows it again;
  - the role switch badge is drawn again; a white bubble under it with a tail
    says 「누르면 학생 화면으로 바뀌어요」 · 「누르면 사장님 화면으로 바뀌어요」;
  - the glass card in the middle (rgba(255, 255, 255, 0.08), 1px
    rgba(255, 255, 255, 0.14) border, 20px radius): 「둘러보기 모드예요」, 「서버까지
    구현한 실제 서비스 기능이에요」 · 「체험할 수 있게 예시 데이터를 넣어 놨어요」,
    and three items with icons (사장님 화면 · 사업자등록번호 없이, 학생 화면 · 학교
    메일 인증 없이, 다른 회원과 주고받으려면 · 카카오로 로그인);
  - the card rises in at 0.5s, the bubble at 1s, 「알겠어요」 at 1.5s.

## Rationale

- Redrawing the lit parts at their measured places keeps them above the dim
  layer without changing the stacking of the home itself.
- One frame keeps the two guides' dim, ✕, and 「알겠어요」 the same.
- Keeping the flags on the device or tab needs nothing from the backend;
  seeing the signup guide twice on two devices is acceptable.

## Alternatives Considered

- A server 「가입 후 안내 봄」 value: not provided by the backend.
- Raising the real card, bell, button, and badge above the dim layer with
  `z-index`: they live in different stacking contexts (scroll area, app bar,
  floating button).
- Drawing the dim layer as `::before`: the in-app preview browser sometimes
  skipped painting it, so it is an element.

## Agent Guidance

- Keep `data-guide="bell"`, `data-guide="demo-badge"`, and the
  `main-tab-screen__fab` class; the guides find the elements by these.
- A new first guide uses `GuideOverlay` and `useGuideMeasure`.

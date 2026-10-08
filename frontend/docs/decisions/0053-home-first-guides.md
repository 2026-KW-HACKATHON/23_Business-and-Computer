# 0053. First guides over the home

## Status

Accepted. Right after signing up, the first home shows 「가입 후 첫 안내」 once:
the home dims, 확인할 일, the bell, and the floating button stay lit, and
three numbered tips and a glowing 「알겠어요」 appear one after another.

## Context

- Figma A-2 「사장님 홈 - 가입 후 첫 안내」 (node 2774-4221) and 「학생 홈 - 가입
  후 첫 안내」 (node 2786-6705), with the overlay sets 「가입 후 첫 안내 / 말풍선
  차례로」 (nodes 3359-4635 · 3359-7680) and 「첫 안내 / 알겠어요 (반짝임)」
  (node 3354-4591).
- The new account's home is 「처음」 (ADR 0051), so the lit card is the 「처음」
  card.
- The backend keeps no 「가입 후 안내 봄」 value (answer of 10/3); the
  frontend remembers it.

## Decision

- **When** (`src/lib/signupGuide.ts`): a successful POST /auth/owner or
  /auth/student stores the role under `signupGuide` in localStorage. The
  home of that role shows the guide while the key matches and the home is
  「처음」. 알겠어요, ✕, a tap anywhere, or Esc removes the key, so it never
  comes back on this device. Another device may show it once more.
- **Overlay** (`SignupGuide`, a portal as wide as the app, 390px at most):
  - the home dims to rgba(12, 12, 12, 0.78) over 0.4s;
  - the 「처음」 card is drawn again at its place, not clickable, its button
    at 40%; the bell sits on a 44px white circle; the floating button is
    drawn at 55%. Their places are measured from the screen (the card ref,
    `[data-guide="bell"]` on the app bar bell, `.main-tab-screen__fab`) and
    again on resize;
  - 「골목인턴에 오신 걸 환영해요」 (24px, white) 14px above the card;
  - tips on `--color-main` pills: ① under the card 「진행 상황은 여기 확인할
    일에서 봐요」, ② left of the floating button 「새 의뢰는 여기서 올려요」 ·
    「새 제안은 여기서 써요」, ③ left of the bell 「학생 제안이 오면 알림으로
    알려 드려요」 · 「사장님 답이 오면 알림으로 알려 드려요」. They rise in at
    0.5s, 1s, and 1.5s;
  - 「알겠어요」 (role-color button, 20px from the bottom) rises in at 2s, and
    a role-color glow behind it spreads and fades every second;
  - ✕ at the top right. The web has no status bar, so it sits over the dimmed
    내 정보 icon; its background is solid (#3a3a3a) so the icon does not show
    through.
  - `prefers-reduced-motion` shows everything at once without the glow.

## Rationale

- Redrawing the lit parts at their measured places keeps them above the dim
  layer without changing the stacking of the home itself.
- Keeping the flag on the device needs nothing from the backend; seeing the
  guide twice on two devices is acceptable.

## Alternatives Considered

- A server 「가입 후 안내 봄」 value: not provided by the backend.
- Raising the real card, bell, and button above the dim layer with
  `z-index`: they live in different stacking contexts (scroll area, app bar,
  floating button).

## Agent Guidance

- Keep `data-guide="bell"` on the app bar bell and the
  `main-tab-screen__fab` class on the floating button; the guide finds them
  by these.

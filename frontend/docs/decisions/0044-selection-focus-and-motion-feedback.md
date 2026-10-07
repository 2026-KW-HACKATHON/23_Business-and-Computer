# 0044. Selection, focus, and motion feedback

## Status

Accepted. Picked tiles rise with a shadow instead of a black border, inputs
show the role color while focused, and the floating button folds into a
circle with motion. The 둘러보기 home badge shows only 「○○으로 보기 ⇄」, and
the confirm screens show picked photos like the detail screens.

## Context

- Selected tiles (탐색 분야 칸, 새 의뢰 · 새 제안 분야 카드) used a 2px black
  border, and focused inputs a black border; the user wanted a raised look
  for selection and the role's main color for focus.
- The 「+ 새 의뢰」 · 「+ 새 제안」 button dropped its label at once when the
  list scrolled.
- The demo strip above every screen already says 「둘러보기 중」, so the home
  badge repeated it.
- 의뢰 등록 3/3 and 제안 보내기 4/4 drew picked photos as one wide tile, which
  cropped them; the detail screens use square thumbnails that open large.

## Decision

- **Selection**: a selected tile has a white background, rises 2px, and casts
  a soft shadow with a thin inner shade at the bottom (`FieldFilter`,
  `.owner-new__field--selected`, `.student-new__field--selected`), over 0.2 s.
- **Focus**: inputs use `--role-focus` for the focused border (`TextField`,
  `request-field` text area and due dates, 가게 소개, 자격증 칸).
  `RoleColorScope` (auth feature) writes the role of the current address on
  `<html data-role>` — addresses under /owner, /explore/, /payments/, and
  /signup/owner → owner (`--color-owner`); /student and /signup/student →
  student (`--color-student`); other screens keep black. The guard and the
  scope share the prefixes in `src/features/auth/lib/routeRole.ts`.
- **Floating button**: the label stays in the DOM; on scroll its grid column
  shrinks from `1fr` to `0fr` and fades while the plus grows from 20 to 24 px,
  over 0.28 s, ending in a 52 px circle.
- **App bar**: 14px between the bell and 내 정보.
- **Photos**: the confirm screens use `ReferencePhotos`, which now also takes
  `blob:` preview URLs.
- `prefers-reduced-motion` turns the transitions off.

## Rationale

- A raised tile reads as picked without adding a dark outline to colorful
  icons.
- Taking the role from the address needs no prop on every input, and the
  signup screens get their color before the user has a role.

## Alternatives Considered

- A `role` prop on `TextField` and the other inputs: rejected, every form page
  would pass it and pages with their own inputs would still need the color.
- Taking the role from the token: rejected, signup screens have no role yet
  and 둘러보기 switches roles while the old screen is still open.

## Agent Guidance

- A new selectable tile uses the raised style above, not a border. A row that
  scrolls sideways needs about 8px of padding above and below the tiles so the
  shadow is not cut.
- A new input's focused border uses `var(--role-focus)`. A new role-only
  address prefix goes in `src/features/auth/lib/routeRole.ts`.

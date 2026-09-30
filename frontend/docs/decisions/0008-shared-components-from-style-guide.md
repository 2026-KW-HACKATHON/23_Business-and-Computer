# 0008. Shared components from the style guide

## Status

Accepted

## Context

Figma pages 「1. 공통」, 「2. 사장님 화면」, and 「3. 학생 화면」 use about thirty
shared components from 「0. 스타일 가이드」. Many had no code counterpart, so
each screen would have rebuilt them. The main-tab app bar (logo or title +
알림 + MY) appeared hand-drawn on ten screens with no shared component at all.

## Decision

- Add a code component for every style-guide component that pages 1–3 use:
  `MainAppBar`, `TabBar`, `SearchBar`, `SummaryCard`, `KindTabs`,
  `FieldFilter`, `CategoryBadge`, `DeadlineBadge`, `EmpathyCount`,
  `DemoRoleBadge`, `RoleAvatar`, `StoreInfo`, `UniversityField`, `Fab`,
  `DownloadButton`, `ResendButton`, plus `MaskIcon` for single-color icons
  that change color (tab and search icons).
- Figma gets the missing 「앱바 / 메인 탭」 component set, and each
  style-guide component description ends with 「코드: <Component … />」 so
  design and code point at each other.
- Style-guide components no screen on pages 1–3 uses move to the section
  「보관 · 9.30 …」 on page 0 instead of being deleted. Their unused code
  images (`iconTabRequest`, `iconLightbulb`) are removed.
- 대분류 values use the Korean names from Notion (`Field` in
  `src/types/field.ts`) until the backend defines its own codes.

## Rationale

- One component per style-guide entry keeps screens thin and makes design
  changes a one-place edit.
- Archiving keeps instances on the archive pages (4·5) linked.

## Alternatives Considered

- Building components only when a screen needs them: rejected, the team
  asked for the shared set up front before the owner and student screens.
- English codes for 대분류: deferred until the backend contract exists.

## Agent Guidance

- Before drawing UI on a screen, check `src/components/index.ts` and the
  Figma description 「코드: …」.
- `MaskIcon` must quote the URL in `mask-image`: Vite inlines small SVGs as
  data URLs containing single quotes, and an unquoted `url()` silently fails
  (the icon renders as a solid square).
- When the backend adds 대분류 codes, map them to `Field` in the feature's
  api layer rather than changing the components.

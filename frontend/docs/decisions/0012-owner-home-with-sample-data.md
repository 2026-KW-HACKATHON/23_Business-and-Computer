# 0012. Owner home with sample data before backend integration

## Status

Accepted

## Context

Figma 「2. 사장님」 has the owner home (사장님 홈 개선안): 확인할 일 as one
card per task that slides every 4 seconds, 학생이 작업 중, 기다리는 중, 이런
의뢰는 어때요? (horizontal example cards), and 끝난 일 that shows the latest
one until 펼치기. Only the middle area scrolls; the app bar stays, and the
「+ 새 의뢰」 button and the glass tab bar float at the bottom. The backend has
no API for this screen yet, and the same screens will later serve the demo
(둘러보기) with fixed data.

## Decision

- /owner renders `src/pages/OwnerHomePage.tsx`. Owner paths live in
  `src/features/owner/lib/paths.ts`; any /owner path without a screen yet
  redirects to /owner.
- The page reads its data only through `useOwnerHome`
  (`src/features/owner/hooks/useOwnerHome.ts`), which returns the Figma
  sample in `src/features/owner/lib/sampleHome.ts`. Dates are `YYYY-MM-DD`
  and turned into 「9월 29일」 by `src/lib/date.ts`.
- 확인할 일 cards are typed by what happened (`draftArrived`,
  `proposalArrived`, `applicants`), and `TodoCarousel` writes the status,
  detail, and button text from those fields.
- Empty lists hide their section; 이런 의뢰는 어때요? is always shown.
- New shared components: `MainTabScreen` (app bar, scroll area, FAB that
  collapses after scrolling, tab bar), `SectionHeader`, `TaskRow`,
  `ChatButton`, and `WorkKindIcon`. `Button` gained `size` (52 / 48 / 40px).
- The looping carousel logic moved to `src/hooks/useLoopCarousel.ts` and is
  shared by `UsageCardCarousel` and `TodoCarousel`.

## Rationale

- One data hook keeps the swap to the API (and to demo fixtures) in a single
  place while the screen code stays the same.
- Typing todos by event instead of storing display strings lets the API send
  raw facts and keeps the wording in the frontend.

## Alternatives Considered

- Writing the sample data inside the page: rejected, it would spread the
  later API swap across screens.
- Separate demo and real screens: rejected, the demo will reuse these
  screens with fixed data instead of keeping a second copy.

## Agent Guidance

- Backend integration still to do: replace `useOwnerHome` with an API call
  (with loading and error states) and design empty states for a new owner.
- The example card passes `exampleId` in router state to /owner/requests/new;
  the request form should prefill from it when it is built.
- Add new owner screens to `src/App.tsx` above the /owner fallback.

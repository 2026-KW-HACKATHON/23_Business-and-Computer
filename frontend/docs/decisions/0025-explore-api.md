# 0025. Student 탐색 list calls GET /explore

## Status

Accepted. The student 탐색 list now reads the backend instead of the sample
data of ADR 0018.

## Context

The student 탐색 tab (/student/explore) read sample requests and proposals.
The backend (dev) has:

- GET /explore?type&specialtyCategoryId&sort&size&cursor for any active
  user (owner or student) in the same demo session.
  - type ALL (default), PROPOSAL, or JOB; cancelled jobs never appear.
  - sort LATEST (default), OLDEST, or LIKES. LIKES is only allowed with
    type PROPOSAL; otherwise 400 COMMON_400.
  - size 1–100 (default 20). The cursor is opaque and only valid for the
    same type, sort, and category; a mismatch is 400 COMMON_400.
  - Answer: { items, nextCursor, hasNext }. Each item has type
    PROPOSAL (proposalId, title, storeName, likeCount,
    specialtyCategories) or JOB (jobId, storeName, title, progressStage,
    status, draftDeadline, finalDeadline, specialtyCategories).
    specialtyCategories is [{ id, name, specialties: [{ id, name }] }].
- There is no title or store search, no 「liked by me」, no student name on
  proposal cards, and no budget or 「already applied」 on job cards. There is
  no API to like a proposal.

## Decision

- **New feature** `src/features/explore` (public entry
  `src/features/explore/index.ts`):
  - `fetchExplore` in `src/features/explore/api/exploreApi.ts` uses
    `apiData` with size 20.
  - `useExplore` (`src/features/explore/hooks/useExplore.ts`) keeps the
    loaded pages, `hasNext`, a separate state for the next page (`more`:
    idle / loading / error), `loadMore`, and `reload`. A new filter starts
    again from the first page; responses for an old filter or retry are
    dropped. 401 goes to /login.
  - `useExploreFeed` turns the screen's tab, field, and sort into the query.
- **Field filter**: the six Figma fields map to a server category id by
  name through `useSpecialties` (no hardcoded ids). While the specialty
  list loads the list shows loading; if it fails, 「다시 시도」 reloads the
  specialty list; a field with no category of that name shows the empty
  text without a request.
- **Sort**: the 「최신순」 text button shows the current sort and opens
  `ExploreSortSheet` (최신순 · 오래된순 · 공감 많은 순). 공감 많은 순 is offered
  only on the 제안 tab; switching to another tab sets the sort back to
  최신순, and the query never sends LIKES with another type.
- **Search**: client side, over the cards loaded so far (title or store
  name). The list keeps loading pages as the end comes into view, so a
  search reaches later pages too.
- **Infinite loading**: `useLoadMoreSentinel` watches an empty element after
  the list (IntersectionObserver, 200 px early). `LoadNotice` shows the first
  page's loading / failure with 「다시 시도」, and the next page's loading /
  failure under the list (its 「다시 시도」 asks for the same cursor again).
  「조건에 맞는 의뢰·제안이 없어요」 shows only when nothing matches and there is
  no next page.
- **Cards** (`src/features/student/components/RequestCard.tsx`,
  `src/features/student/components/PeerProposalCard.tsx`):
  - One `CategoryBadge` per distinct category name, then the store name.
  - Job: draft-deadline badge and 「지원하기」 while OPEN; status text
    OPEN 「모집 중」, AWAITING_START / MATCHED 「진행 중」, CLOSED 「완료」;
    「의뢰서 전체 보기 ›」 always in the footer row. Links use the job id.
  - Proposal: the empathy count only. 「내 제안이에요」 when the id is
    in `useSentProposals` (GET /me/proposals); mine opens the sent-proposal
    detail, others the peer-proposal route.
- **Missing fields are requested from the backend and have slots**: the
  card types carry optional fields that the screen shows only when the
  server sends them, and never fills with guesses:
  - job `budget` → the 「예산」 line; job `applied` → 「지원했어요」;
  - proposal `studentName` → 「○○ 학생 → 가게」; proposal `likedByMe` →
    a filled heart.
- The description under the title reads 「가게 의뢰에 지원하고, 다른 학생의
  제안을 손님 눈으로 둘러보세요.」

## Rationale

- One hook for the list keeps paging, late-response handling, and the 401
  redirect in one place for both roles' 탐색.
- Mapping fields by name follows ADR 0020 and ADR 0021: the server owns the
  ids.
- Showing optional fields only when present avoids fake budgets or
  「지원했어요」 that the server never said.

## Alternatives Considered

- Loading every page up front for a complete search (like 가게 탐색, ADR
  0020): rejected, the 탐색 list grows with every request and proposal.
- Hardcoding category ids: rejected, see ADR 0021.

## Agent Guidance

- The request detail, apply, peer-proposal detail, the home 「다른 학생들의
  제안 공감하기」, and the owner 탐색 screens are wired to the API in later
  steps; extend this ADR then.
- When the backend adds a slot field above, only the type comment needs a
  look; the screens already show it.

# 0013. Owner screens reached from the home

## Status

Accepted

## Context

The owner home (ADR 0012) links to thirteen screens in Figma 「2. 사장님」:
the tabs 탐색 and 채팅, 알림 and 내 정보 from the app bar, 작업 확인 · 받은
제안 상세 · 지원자 목록 from the 확인할 일 cards, 채팅방 (its work card opens
the work's documents, ADR 0045), 보낸 의뢰 상세 from 학생이 작업 중 and 기다리는 중
(ADR 0049), 지난 결과물 보기
from 끝난 일, and 의뢰 등록 1/3 from 「+ 새 의뢰」 and the example cards. The
Notion 「화면 상태 전환표」 fixes their routes. The backend has no API for
them yet.

## Decision

- Routes follow Notion: /owner/explore, /owner/chats, /owner/chats/:roomId
  (ADR 0034),
  /owner/notifications, /owner/me, /owner/works/:workId/check,
  /owner/works/:workId/result, /owner/proposals/:proposalId,
  /owner/requests/:requestId, /owner/requests/:requestId/applicants, and
  /owner/requests/new. The home buttons now use these paths
  (`src/features/owner/lib/paths.ts`); 「더 보기 ›」 goes to 탐색.
- Every screen reads sample data through hooks in
  `src/features/owner/hooks/useOwnerData.ts`. The sample lives in
  `src/features/owner/lib/sampleDetails.ts` and shares ids with the home
  sample, so a work id opens the same work everywhere. Sample dates count
  from today (`src/lib/sampleTime.ts`).
- A missing id shows `OwnerMissing` instead of an empty screen.
- The home rows of 학생이 작업 중, 기다리는 중, and 끝난 일 are whole-row
  buttons with a ›; `TaskRow` takes `onClick` for this.
- A picked profile photo lives only in the screen until the backend is
  wired. Chat (ADR 0034) and 알림 read and write the backend.
- 의뢰 등록 1/3 starts empty from the FAB and with the example's field and
  task picked when opened from an example card (`exampleId` in router
  state). Tasks per field reuse `SPECIALTY_BADGES`.
- New shared components: `SubScreen` (← title app bar, scroll body, fixed
  footer), `MenuList`, `ExploreCard`, `ChatRow`, `NotificationRow`,
  `InfoRows`, `LabelChip`, `AttachmentTiles`, `NoteBox`, and `WorkPlan`.
  `RoleAvatar` and `ProfilePhoto` gained a size, `AppBar` takes a rich
  title, and the field icon map moved to
  `src/components/FieldFilter/fieldIcons.ts`.

## Rationale

- Sharing ids across the sample keeps the walk-through consistent, which
  the demo (둘러보기) will need too.
- `SubScreen` gives every non-tab screen the same fixed app bar and footer,
  matching the Figma 「스크롤 화면」 notes.

## Alternatives Considered

- Leaving the home buttons on the earlier paths: rejected, Notion is the
  source of truth for routes.

## Agent Guidance

- The screens one level further (registration 2/3 · 3/3, payment,
  revision, review, cancel, explore details) are in ADR 0014.
- Backend integration: replace the hooks in `useOwnerData.ts`, and serve
  files through signed URLs for 「받기」. Chat (ADR 0034) and 알림 are wired.

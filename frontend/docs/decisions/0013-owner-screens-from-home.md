# 0013. Owner screens reached from the home

## Status

Accepted

## Context

The owner home (ADR 0012) links to thirteen screens in Figma 「2. 사장님」:
the tabs 탐색 and 채팅, 알림 and 내 정보 from the app bar, 작업 확인 · 받은
제안 상세 · 지원자 목록 from the 확인할 일 cards, 채팅방 and the 작업계획서
sheet from 학생이 작업 중, 보낸 의뢰 상세 from 기다리는 중, 지난 결과물 보기
from 끝난 일, and 의뢰 등록 1/3 from 「+ 새 의뢰」 and the example cards. The
Notion 「화면 상태 전환표」 fixes their routes. The backend has no API for
them yet.

## Decision

- Routes follow Notion: /owner/explore, /owner/chats, /owner/chats/:workId,
  /owner/notifications, /owner/me, /owner/works/:workId/check,
  /owner/works/:workId/result, /owner/proposals/:proposalId,
  /owner/requests/:requestId, /owner/requests/:requestId/applicants, and
  /owner/requests/new. The home buttons now use these paths
  (`src/features/owner/lib/paths.ts`); 「더 보기 ›」 goes to 탐색.
- Every screen reads sample data through hooks in
  `src/features/owner/hooks/useOwnerData.ts`. The sample lives in
  `src/features/owner/lib/sampleTabs.ts` and `sampleDetails.ts` and shares
  ids with the home sample, so a work id opens the same work everywhere.
  Chat and notification times count back from now
  (`src/lib/sampleTime.ts`) so 「10분 전」 and 「어제」 read
  naturally.
- A missing id shows `OwnerMissing` instead of an empty screen.
- The home rows of 학생이 작업 중, 기다리는 중, and 끝난 일 are whole-row
  buttons with a ›; `TaskRow` takes `onClick` for this.
- 작업계획서 보기 is a bottom sheet (`WorkPlanSheet`) opened from the home
  and the chat room, not a route. From the home it adds 「채팅하기」.
- 알림 read state, sent chat messages, and a picked profile photo live only
  in the screen until the backend is wired.
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
- A route for the 작업계획서 sheet: rejected, Notion lists it as a popup.

## Agent Guidance

- The screens one level further (registration 2/3 · 3/3, payment,
  revision, review, cancel, explore details) are in ADR 0014.
- Backend integration: replace the hooks in `useOwnerData.ts`, mark
  notifications read on the server, send chat messages over the planned
  WebSocket, and serve files through signed URLs for 「받기」.

# 0017. One source for the owner sample data

## Status

Accepted. Replaces the sample-data parts of ADR 0012 (dates as fixed
`YYYY-MM-DD`, the home reading its own sample) and ADR 0013 (separate list
samples for 탐색, 채팅, and 결제 내역).

## Context

Checking every owner screen against Figma and against each other showed the
sample data disagreeing with itself. The same student had a different
department on the home and in 내 활동, a request with applicants never showed
on the home, fixed dates had already passed (an auto-complete date before
today), and a work plan promised dates that did not match the request. The
home, 결제 내역, 채팅 목록, and 탐색 목록 each kept their own copy, so a
detail screen did not always continue what its list showed. In the demo,
완료 확인 → 후기 → 결과물 led to 「아직 끝나지 않은 작업이에요」 because the
work never became completed.

## Decision

- Single source: the home 「이런 의뢰는 어때요?」 examples live in
  `src/features/owner/lib/sampleHome.ts`, and `useRequestExample`
  (`src/features/owner/hooks/useOwnerData.ts`) opens 의뢰 등록 with one of
  them. Everything else reads the backend: received proposals (ADR 0025),
  탐색 (ADR 0026), sent requests, their applicants, and cancel (ADR 0030),
  in-progress works (ADR 0035), finished works (ADR 0036), the student
  profile (ADR 0038), and 결제 내역, its summary, and 내 정보 (ADR 0040).
- Example dates count from today with `day(offset)` in
  `src/lib/sampleTime.ts`.
- Chat reads the backend (ADR 0034).
- 「후기 남기기」 marks the work with `markOwnerWorkReviewed` until the page
  reloads, so 내 활동 완료, 작업 이력, and the chat room show 「후기 작성 완료」
  before the finished list reloads.
- `OwnerHome.firstVisit` comes from the home's lists (ADR 0051). When true,
  확인할 일 holds `FirstVisitGuide` (피그마 「사장님 홈 - 처음」) and 학생이 작업
  중, 기다리는 중, and 끝난 일 are hidden.
- 보낸 의뢰 and 진행 중 in 내 활동 are sorted by 초안 마감, earliest first.
- 「신고」 on 초안 확인 and 「문제 신고」 in the chat room open `ReportSheet`.
  When the chat room shows its work actions is in ADR 0034.
- The 「안전결제가 완료됐어요」 notification opens 내 활동 진행 중, as in Notion.
- Passed segments of `FlowBar` are black.

## Rationale

- One source keeps an example card and the 의뢰 등록 it opens in step, and
  screens read data only through the hooks.
- Relative dates keep the example deadlines sensible on any demo day.

## Alternatives Considered

- Fixing each copy by hand: the copies drift again with the next edit.
- Keeping the 후기 mark in storage: the finished list's `reviewed` shows it
  after a reload.

## Agent Guidance

- Add home examples only to `sampleHome.ts`; works, requests, proposals, and
  student profiles come from the backend.
- Use `day` for example dates, not fixed strings.

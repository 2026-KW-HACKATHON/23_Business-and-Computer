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

- Single sources: works, requests, and proposals in
  `src/features/owner/lib/sampleDetails.ts`, student profiles in
  `sampleStudents.ts`, and explore details in `sampleExplore.ts`. The home,
  내 활동, 결제 내역 and its summary, the chat list, the explore list, and the
  내 정보 counts are built from them in
  `src/features/owner/hooks/useOwnerData.ts` and `useOwnerHome.ts`.
- A work, request, or proposal refers to a student by id. Name, department,
  year, rating (the average of the profile's reviews), and completed count
  always come from the profile.
- Sample dates count from today with `day(offset)` and `dayAt(offset, h, m)`
  in `src/features/owner/lib/sampleTime.ts`. Plans say 「초안 마감일까지」
  instead of a date so they always agree with the request.
- Each work has `paidOn`; 결제 내역 and 「이번 달 결제」 use it. Chat threads
  carry `unreadCount`. Explore proposals and requests carry `progress`
  (수락 대기, 수락됨, 완료) and `createdAt`.
- Demo state: `completeOwnerWork` (완료 확인) and `markOwnerWorkReviewed`
  (후기 남기기) mark a work in memory, so the result screen, 내 활동 완료, the
  home 끝난 일, and 결제 내역 follow the demo until the page reloads.
- `OwnerHome.firstVisit` comes from the backend later (`SAMPLE_FIRST_VISIT`
  for now). When true, the home shows `FirstVisitGuide` (피그마 「사장님 홈 -
  처음」) instead of 확인할 일, 학생이 작업 중, 기다리는 중, and 끝난 일.
- 보낸 의뢰 and 진행 중 in 내 활동 are sorted by 초안 마감, earliest first.
- 「신고」 on 초안 확인 and 「문제 신고」 in the chat room open `ReportSheet`.
  The chat room shows the work actions only while the work is in progress or
  waiting for a check, and 「작업 취소」 only before the draft arrives.
- The 「안전결제가 완료됐어요」 notification opens 내 활동 진행 중, as in Notion.
- Passed segments of `FlowBar` are black.

## Rationale

- Deriving lists from one source keeps a list and its detail screen in step,
  and the hooks stay the only place to swap in the API.
- Relative dates keep deadlines, auto-complete dates, and 「이번 달」 sensible
  on any demo day.

## Alternatives Considered

- Fixing each copy by hand: the copies drift again with the next edit.
- Persisting demo state in storage: a reload resetting the demo is simpler to
  present.

## Agent Guidance

- Add owner sample data only to the single sources and derive the rest in
  the hooks. Never copy a student's name or department into a work.
- Use `day` / `dayAt` for new sample dates, not fixed strings.
- When the API arrives, replace the bodies of the hooks in
  `useOwnerData.ts` and `useOwnerHome.ts`, and take `firstVisit` from the
  backend instead of `SAMPLE_FIRST_VISIT`.

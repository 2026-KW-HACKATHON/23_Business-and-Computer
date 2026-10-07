# 0049. Working jobs open their detail screens

## Status

Accepted. The owner home 「학생이 작업 중」 rows and 내 활동 › 진행 중
「상세보기」 open a detail screen, like 「기다리는 중」: 보낸 의뢰 for a request,
받은 제안 상세 for a proposal. 보낸 의뢰 shows the work while a student is on
it, and its button reads 「지원자 N명 보기」 when applicants are waiting.

## Context

- 「학생이 작업 중」 and 「기다리는 중」 sit next to each other on the home, and
  「기다리는 중」 rows open 보낸 의뢰. The user asked every row to open a
  detail screen the same way.
- 보낸 의뢰 showed only the 의뢰서 and the 「학생을 고르면 이렇게 진행돼요」
  steps after a student was picked, and its button was always 「확인」, even
  with applicants waiting.
- The backend has the student and stage in GET /me/jobs?status=MATCHED and
  the application in GET /me/chat-rooms (one room per paid job). Nothing new
  is needed from it.

## Decision

- **Rows**: home 「학생이 작업 중」 → `OWNER_PATHS.request(jobId)`, or
  `OWNER_PATHS.proposal(proposalId)` for a proposal. 내 활동 › 진행 중
  「상세보기」 → 작업 확인 when a submission arrived, otherwise the same two.
  The 작업계획서 sheet (`WorkPlanSheet`) opens from the chat room
  「작업 보기」.
- **보낸 의뢰 while MATCHED** (`src/pages/OwnerRequestPage.tsx`,
  `useAssignedWork` → `loadAssignedWork` in
  `src/features/owner/lib/progressJobs.ts`), top to bottom:
  - the status 「초안 제작 중」 · 「수정안 제작 중」 · 「초안이 도착했어요」 from
    the matched list (「진행 중」 when the job is not in it);
  - the flow bar 의뢰 → 시작 → 초안 / 수정 with 「작업 중」 under the current
    step (「확인할 차례」 when a submission waits; 초안 · 「작업 중」 when the
    job is not in the list);
  - `StudentBox` (`src/features/owner/components/StudentBox.tsx`): the
    student, 「NN학번 · 학과」, and 「프로필 보기」 → the student profile. 받은
    제안 상세 uses the same box;
  - the terms, then 「작업계획서」 (한 줄 요약 · 작업계획서 · 결과물) from the
    chat room, hidden when the room has no application;
  - 할 일, 맡기고 싶은 일, 참고 자료.
  - Loading or failing to load the student shows `LoadNotice` with 「다시
    시도」; a failed chat-room list only hides 「작업계획서」. 401 → /login.
- **보낸 의뢰 while OPEN**: 「의뢰 취소」 and the 「학생을 고르면 이렇게
  진행돼요」 steps show only now. With applicants the button reads 「지원자 N명
  보기」 and opens the applicant list; with none it is 「확인」.

## Rationale

- One rule for the home lists: a row opens the screen of its 의뢰 or 제안.
- The student's plan sits next to the 의뢰서 it answers, as the 작업계획서 sits
  in 받은 제안 상세.

## Alternatives Considered

- A separate route for a matched request: not needed, the 의뢰 id already
  identifies the work.
- Waiting for the matched list to carry the application: the chat-room list
  has it today.

## Agent Guidance

- When GET /me/jobs?status=MATCHED or GET /jobs/{id} carries the application,
  read it there and drop GET /me/chat-rooms from `loadAssignedWork`.
- Owner detail screens that show a student use `StudentBox`.

# 0065. The student home reads GET /me/home

## Status

Accepted. The student home loads every section from one `GET /me/home`
call. It replaces the six list calls of ADR 0023, 0026, 0027, 0032, and
0042 on the home only; 내 활동, 내 정보, and the other screens keep their
hooks.

## Context

- The home built itself from GET /me/proposals, GET /me/job-applications,
  GET /me/jobs?status=MATCHED, GET /settlements (with GET /me/proposals for
  the work kind), and GET /explore (likes order, minus my proposals). Each
  list had its own loading, retry, and first-visit rule.
- The backend added `GET /me/home` for any signed-in user. The answer
  follows the role; an account that has not picked a role gets 403
  `HOME_403`. The student answer is
  `{ role, firstVisit, todos, checking, waiting, peerProposals, done }`:
  - `todos`: proposals whose request came (AWAITING_START, job not
    cancelled) first, then drafting and revising jobs by due date. Each has
    `type` (`proposalAgreement` · `drafting` · `revising`), `kind`, `jobId`,
    `proposalId`, `title`, `storeName`, `categories` (category names, no
    repeats), and `stage` · `due` (drafting: draft deadline; revising: final
    deadline);
  - `checking`: submitted jobs without a revision request, with
    `submissionType` and `submittedOn`;
  - `waiting`: PENDING proposals (`likeCount`), then PENDING applications
    (`jobApplicationId`, `draftDeadline`);
  - `peerProposals`: the top 5 by likes minus my own, first 2, with
    `studentName`, `storeName`, `status`, `likeCount`, `likedByMe`;
  - `done`: settled (SETTLED) jobs, latest first, with `completedOn`.
  - A section that failed is `null` (send the same request again to retry);
    an empty list means it loaded with nothing. `firstVisit` is true when
    there is no history at all, false when any history is seen, and null
    when a failed source leaves it unknown. Dates are Korean
    「YYYY-MM-DD」.

## Decision

- **API**: `src/features/student/api/homeApi.ts` has the response types and
  `fetchStudentHome()` (`apiData`). `src/features/student/lib/studentHome.ts`
  turns the answer into the home types of `src/features/student/types.ts`
  (`StudentTodo`, `StudentCheckingItem`, `StudentWaitingItem`,
  `StudentPeerProposal`, `StudentDoneItem`) and keeps `null` sections as
  `null`. Rows without the id their screen needs are dropped.
- **Hook** (`useStudentHome`): one request. `load` is loading, error, or
  loaded with `retrying`. `reload` sends the request again; while a home is
  shown it stays on screen and only the failed sections show loading. If
  that retry fails, the home stays and the sections return to
  「다시 시도」. 401 goes to /login. The 「이런 제안은 어때요?」 examples stay
  frontend sample data (ADR 0018).
- **Screen** (`src/pages/StudentHomePage.tsx`), same sections, texts, and
  links as before:
  - Whole request loading or failed: one `LoadNotice` 「홈을 불러오는
    중이에요」 · 「홈을 불러오지 못했어요」 with 「다시 시도」.
  - A `null` section keeps its header without a count and shows
    `LoadNotice` 「… 불러오지 못했어요」 with 「다시 시도」 (확인할 일,
    사장님이 확인 중, 기다리는 중, 끝난 일). 다른 학생들의 제안 공감하기
    stays hidden when it failed, as in ADR 0026.
  - First visit: the server's `firstVisit` when it is sent. When it is
    null, the home is not a first visit if any shown list has an item;
    otherwise it is unknown and the home shows only the 「다시 시도」 line,
    never the guide (ADR 0023, ADR 0051).
  - 확인할 일 cards use `categories` as the badges and `due` for
    「초안 마감 : M월 D일」. 사장님이 확인 중 reads 「수정안」 when
    `submissionType` is `REVISION`, and `submittedOn` for the date. 기다리는
    중 shows 「가게에 보낸 제안」 · 「손님 N명 공감」 and 「가게 의뢰에 지원」 ·
    「초안 마감 : M월 D일」. 끝난 일 shows `completedOn` as 「완료 : M월 D일」.
  - Links: 의뢰서가 온 제안 → 조건 확인 (`proposalStart`); 초안 차례 →
    초안 제출; 수정안 차례 → 수정 요청 확인 (상세보기) and 수정안 제출
    (button); 사장님이 확인 중 → 제출한 결과물; 보낸 제안 → 보낸 제안서
    상세; 지원 → 의뢰서 전체 보기; 다른 학생 제안 → its detail; 끝난 일 →
    내 결과물.
  - `PeerProposalRow` takes the title, student name, store name, and status
    of `StudentPeerProposal`; liking from the home still goes through
    `useProposalLikes`.

## Rationale

- One request is faster than six and gives one loading state, and the
  server already joins store names, category names, and my likes.
- Keeping a section's failure in its own place matches the answer, which
  can fail one section without the rest.

## Alternatives Considered

- Keeping the list hooks and reading GET /me/home only for the first-visit
  value: still six requests, and two sources could disagree.
- Showing the peer section's failure: it is secondary, and ADR 0026 hides it.

## Agent Guidance

- A new student home section belongs in GET /me/home; do not add another
  list call to `useStudentHome`.
- Other screens keep their own hooks (`useSentProposals`, `useAppliedJobs`,
  `useProgressJobs`, `useFinishedJobs`).

# 0072. Student job finder: three questions pick open requests to apply for

## Status

Accepted. The student home shows a 「나에게 맞는 일 찾기」 banner above
확인할 일. It opens three questions (fields, weekly hours, budget) and a
result with the open requests that fit best, the student-side twin of the
owner's 「맡길 일 찾기」 (ADR 0068).

## Context

- New students see a long 탐색 list and do not know which request suits
  them. The owner side already has a check-up that turns a few answers
  into suggestions (ADR 0068).
- `GET /explore?type=JOB` gives each request's fields
  (`specialtyCategories`), `budget`, `draftDeadline`, `status`, and whether
  I applied (`applied`). There is no recommendation endpoint, and the
  answers do not need to be stored.

## Decision

- **Banner** (student home, above 확인할 일, also on the first-visit home,
  while the home loads, and in 둘러보기): the same shape and motion as the
  owner banner on a left-to-right gradient from #fdeef3 to #f6cfdc, 「어떤
  일을 해 볼지 모르겠다면?」, 「몇 가지에 답하면 지금 지원하기 좋은 의뢰를
  골라 드려요」, 「1분 만에 찾아보기 ›」 in student color, and the 3D
  「전체」 field icon. No close button.
- **Questions** (/student/job-finder/:step, 1–3, app bar 「나에게 맞는 일
  찾기」, the owner finder layout with student color, progress 「n/3」):
  1. 「어떤 일을 해 보고 싶나요?」: the six field chips (several) or
     「아무 일이나 좋아요」 (alone), then 「다음」.
  2. 「일주일에 시간을 얼마나 낼 수 있나요?」: 3시간이 안 돼요 / 3~8시간쯤
     돼요 / 8시간 넘게 돼요.
  3. 「작업비는 어느 정도면 좋을까요?」: 2만 원이면 충분해요 / 2만~4만 원이
     좋아요 / 4만 원 넘는 일이 좋아요.
  Tapping an answer goes on; 「건너뛰기」 on the app bar opens the result
  with the rest unanswered. Answers travel in the route state
  (`{ jobFinder: { fields, hours, budget } }`, `null` = skipped), so ←
  keeps them. A step whose earlier answers are missing goes to question 1.
- **Scoring** (`src/features/student/lib/jobFinder.ts`): only requests
  that are open, not applied to, and whose draft deadline is today or later.
  With fields chosen, only requests with one of them stay (+3). 「마감
  여유」 +2 when the draft deadline is at least 10 / 5 / 0 days away for the
  three hour answers. Budget in the chosen band +2, in the next band +1.
  Ties go to the earlier draft deadline.
- **Result** (/student/job-finder/result): my answers as gray pills (skipped
  ones left out), 「지금 지원하기 좋은 의뢰 N개」 (top three), and 「다른 의뢰
  N개 더 보기」 / 「다른 의뢰 접기」. A card shows the field badge, title,
  「가게 · 초안 마감 M월 D일」, reason chips (「○○ 분야」 · 「마감 여유」 ·
  「작업비 맞음」), the budget, and 「의뢰서 보기 ›」, and opens 의뢰서 전체
  보기 (apply from there). With nothing left: 「지금 맞는 의뢰가 없어요」
  and the footer 「다시 찾기」 · 「가게에 제안하기」 (제안 보내기 1/4).
  Otherwise the footer is 「다시 찾기」 · 「홈으로」. The requests come from
  `useOpenJobs` (every page of `GET /explore?type=JOB`, up to five pages of
  100).

## Rationale

- Three questions are enough to rank the requests we can read; questions
  without data behind them (team or solo, experience) would only look
  thorough.
- Filtering by field first and then scoring the rest follows how
  recommendation quizzes work, and the reason chips show why a request was
  picked.
- When nothing fits, pointing to a proposal keeps the student moving; our
  two-way structure is the answer to "no requests for me".

## Alternatives Considered

- **Prefilling fields from the profile**: one less tap, but the student may
  want to try another field. The chips start empty.
- **Hiding the result behind a total score**: a number per request adds no
  meaning beyond the order and the reason chips.

## Agent Guidance

- Question copy, bands, and day thresholds live in `jobFinder.ts`; the
  Figma section 「J. 나에게 맞는 일 찾기」 (page 3) shows the same values.
  Change both together.

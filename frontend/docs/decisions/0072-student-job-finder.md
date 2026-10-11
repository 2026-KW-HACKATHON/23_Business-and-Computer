# 0072. Student job finder: three questions pick open requests to apply for

## Status

Accepted. The student home shows a 「나에게 맞는 일 찾기」 banner above
확인할 일. It opens three questions (fields, how much time the student has, budget) and a
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
  일을 해 볼지 모르겠다면?」, 「몇 가지에 답하면 맞는 일을 골라 드려요」 (one
  line), 「1분 만에 찾아보기 ›」 in student color, and the 3D
  「전체」 field icon. No close button.
- **Questions** (/student/job-finder/:step, 1–3, app bar 「나에게 맞는 일
  찾기」, the owner finder layout with student color, progress 「n/3」):
  1. 「어떤 일을 해 보고 싶나요?」: the six field chips (several) or
     「아무 일이나 좋아요」 (alone), then 「다음」.
  2. 「요즘 얼마나 여유가 있나요?」 (「첫 결과물(초안)을 보낼 때까지 필요한
     시간이에요」): 지금 바로 시작할 수 있어요 / 1주쯤 여유를 두고 하고
     싶어요 / 시험·과제로 바빠서 2주는 필요해요.
  3. 「작업비는 어느 정도면 좋을까요?」: 2만 원이면 충분해요 / 2만~4만 원이
     좋아요 / 4만 원 넘는 일이 좋아요.
  Tapping an answer goes on; 「건너뛰기」 on the app bar opens the result
  with the rest unanswered. Answers travel in the route state
  (`{ jobFinder: { fields, pace, budget } }`, `null` = skipped), so ←
  keeps them. A step whose earlier answers are missing goes to question 1.
- **Scoring** (`src/features/student/lib/jobFinder.ts`): only requests
  that are open, not applied to, and whose draft deadline is today or later.
  With fields chosen, only requests with one of them stay (+3). 「마감
  여유」 +2 when the draft deadline is at least 0 / 7 / 14 days away for the
  three answers to question 2. Budget in the chosen band +2, in the next band +1.
  Ties go to the earlier draft deadline.
- **Result** (/student/job-finder/result): my answers as gray pills (skipped
  ones left out), 「지금 지원하기 좋은 의뢰 N개」 (top three), and 「다른 의뢰
  N개 더 보기」 / 「다른 의뢰 접기」. A card shows the field badge, title,
  「가게 · 초안 마감 M월 D일」, reason chips (「○○ 분야」 · 「마감 여유」 ·
  「작업비 맞음」), gray miss chips when an answered question does not fit
  (「마감 빠듯」 · 「작업비 범위 밖」, the request is ranked lower, not
  removed), the budget, and 「의뢰서 보기 ›」, and opens 의뢰서 전체
  보기 (apply from there). With nothing left: 「지금 맞는 의뢰가 없어요」
  and the footer 「다시 찾기」 · 「가게에 제안하기」 (제안 보내기 1/4).
  Otherwise the footer is 「다시 찾기」 · 「홈으로」. The requests come from
  `useOpenJobs` (every page of `GET /explore?type=JOB`, up to five pages of
  100).

## Rationale

- Three questions are enough to rank the requests we can read; questions
  without data behind them (team or solo, experience) would only look
  thorough.
- Requests carry deadlines, not hours of work, so question 2 asks how much
  time the student needs before the first draft and compares it with the
  draft deadline directly. Asking for weekly hours would need a guessed
  hours-per-request to turn into days.
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

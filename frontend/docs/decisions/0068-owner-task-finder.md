# 0068. Owner task finder: 12 questions suggest what to request

## Status

Accepted. The owner home shows a 「맡길 일 찾기」 banner above 확인할 일. It
opens 12 yes/no questions, one per screen, and a result screen with up to
three suggested requests. A suggestion starts 의뢰 등록 filled with an
example, the same way the home 「이런 의뢰는 어때요?」 cards do (B-2).

## Context

- Many owners have never hired anyone for promotion or small IT work, so
  they do not know what they could ask a student to do. Students can already
  send proposals first (ADR 0025, 0026), but an owner who opens the app alone
  still needs a starting point.
- 의뢰 등록 already starts from an example: `exampleId` in the route state
  picks the field and task by name on 1/3 (`exampleRequestChoice`) and fills
  2/3 with the example content (`useRequestExample`).
- The backend has no endpoint for this, and the answers do not need to be
  stored.

## Decision

- **Banner** (owner home, above 확인할 일, also on the first-visit home and
  in 둘러보기), on a left-to-right gradient from #fff4cc to #ffdb9e: the title 「무엇을 맡길지 모르겠다면?」
  (16px bold), one line 「몇 가지에 답하면 맡길 일을 골라 드려요」, the link
  text 「1분 만에 찾아보기 ›」, and the 3D 「전체」 field icon (56px) on the
  right. A 40%-white 170px circle hangs over the top-right corner (the card
  clips it). The whole card is the button. While it is pressed the card
  shrinks to 98%, the circle grows to 112%, the icon tilts −8° and grows to
  108%, and 「›」 moves 4px right. A mouse hover does a smaller version
  (circle 108%, icon −6° and 105%, 「›」 3px). With reduced motion nothing
  moves. It has no close button.
- **Questions** (`/owner/task-finder/:step`, 1–12, app bar 「맡길 일
  찾기」): a progress bar with 「n/12」, the question, the hint 「가게
  상황에 가까운 답을 골라 주세요」, and two answer buttons. Tapping an
  answer goes straight to the next question. The answers travel in the
  route state (`{ answers: (0 | 1)[] }`), so ← returns to the previous
  question with its answers. A step whose earlier answers are missing (for
  example a typed URL) goes to question 1.
- **Data** (`features/owner/lib/taskFinder.ts`): each question has two
  answers, the answer that leads to a suggestion (`need`), a card title, a
  reason line, and an example id. Nine new examples (`finder-*`) sit next
  to the home examples; 리뷰 분석, 예약서, and 도장카드 reuse the home
  examples. `useRequestExample` looks in both lists (`findRequestExample`).
  Example budgets stay between 10,000 and 50,000 won, and the copy does not
  assume the store is a restaurant.
- **Result** (`/owner/task-finder/result`): the first three questions whose
  answer was the `need` answer, in question order, under 「지금 맡기면 좋은
  일 N가지」 (N = the number of cards). With none, 「이런 일도 맡겨 볼 수
  있어요」 shows ⑫ 홍보·이벤트 기획, ⑪ 영상 제작 및 편집, and ⑩ 전단지·포스터
  디자인 with their own reason lines. A card shows the field badge, title,
  reason, 「예시 작업비 N원」, and 「이 일로 의뢰하기 ›」, and opens
  의뢰 등록 with `{ exampleId }`. The footer has 「다시 점검하기」 (secondary,
  question 1) and 「홈으로」 (primary, owner yellow).
- Title and content are filled in full, like the home examples, even if
  several owners post the same text.

## Rationale

- Reusing the `exampleId` path keeps one way to start 의뢰 등록 from an
  example, and the field and task names already match the server list.
- One question per screen keeps each tap small, which suits owners who are
  busy at the counter.
- Keeping the answers in the route state needs no storage and makes ← work
  without extra code.

## Alternatives Considered

- **All questions on one screen**: faster, but reads like a survey form.
- **Empty title and content with the example as a placeholder**: avoids
  identical requests in 탐색, but the owner then has to write everything.
  The team chose to fill them like the home examples.
- **Saving the answers on the server**: not needed for a suggestion, and
  there is no endpoint.

## Agent Guidance

- Questions, buttons, card copy, and prices come from the Figma table
  「질문 12개 · 추천 연결」 in section 「I. 맡길 일 찾기」 (page 2). Change
  that table and `taskFinder.ts` together.
- A new example must use a field and task name that exist in GET
  /specialties, or 의뢰 등록 1/3 starts with nothing picked.

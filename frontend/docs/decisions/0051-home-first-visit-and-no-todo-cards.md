# 0051. The home keeps 확인할 일 with 「처음」 and 「없음」 cards

## Status

Accepted. 확인할 일 stays on the owner and student homes. An account with no
history shows the 「처음」 card in it (the first task, 「골목인턴은 이렇게
써요」, and the first-task button); an account with history but nothing to
check shows the 「없음」 card. The owner home decides the first visit from its
lists.

## Context

- Figma A-2: 「사장님 홈 - 처음」 (node 2472-4125), 「학생 홈 - 처음」 (node
  2474-1672), 「사장님 홈 - 확인할 일 없음 (예시)」 (node 3321-4314), 「학생 홈 -
  확인할 일 없음 (예시)」 (node 3321-7238), and the 확인할 일 card sets
  (현재=처음 nodes 3315-4320 · 3315-4339, 현재=없음 nodes 3315-4312 ·
  3315-4331). The section is never removed; 처음 holds a waving character in a
  circle, 「첫 의뢰를 올려 볼까요?」 · 「첫 제안을 보내 볼까요?」, three steps,
  and the button; 없음 holds 「지금 확인할 일이 없어요」 and the thumbs-up (owner)
  or V (student) character on a circle.
- The code showed a guide with older wording above the sections, the owner
  first-visit value was sample data, and an empty to-do list hid the section.
- The backend has no first-visit value (answer of 10/3): the lists decide.

## Decision

- **Cards** (`src/components`):
  - `TodoStartCard` (tone, title, description, steps with bold words, button
    label and action): 20px padding, 1.5px role-color border, 16px radius, the
    Figma 100px picture (`firstVisitOwner` · `firstVisitStudent`: the circle
    and the character in one SVG), a grey line, 「골목인턴은 이렇게 써요」, and
    numbered steps (22px black circles with `--color-main` numbers).
  - `TodoNoneCard` (tone): 229px tall, 「지금 확인할 일이 없어요」 ·
    「할 일이 생기면 바로 알려 드릴게요」 on two lines each, and
    `doneOwnerThumbsUp` · `doneStudentV` (100px) on a 112px circle
    (`--color-main`, student #98274C at 10%).
- **Owner first visit** (`useOwnerHome`): true when 모집 중인 의뢰 (GET
  /me/jobs?status=OPEN), 받은 제안 (GET /me/received-proposals), 진행 중 (GET
  /me/jobs?status=MATCHED), and 끝난 의뢰 (GET /me/jobs?status=CLOSED) have all
  loaded empty; false when any has an item or any failed; undefined while any
  is loading. True → 확인할 일 「0」 with `FirstVisitGuide` (「첫 의뢰를 올려
  볼까요?」, 「첫 의뢰 올리기」 → 의뢰 등록), then 이런 의뢰는 어때요?; the other
  sections hide.
- **Student first visit**: the existing check (ADR 0018) shows 확인할 일 「0」
  with `StudentFirstVisitGuide` (「첫 제안을 보내 볼까요?」, 「첫 제안 쓰기」 →
  제안 보내기), then the examples and 공감하기.
- **없음**: when the account is not new and 확인할 일 has loaded empty (owner:
  받은 제안 and 진행 중 loaded; student: 진행 중 and 보낸 제안 loaded), the
  section shows 「0」 and `TodoNoneCard`. The rest of the home is unchanged;
  new work starts from the floating button.

## Rationale

- Keeping the section shows where things will appear, and the two cards use
  the same frame as the to-do cards.
- Deciding from the lists needs nothing new from the backend and matches the
  student home.

## Alternatives Considered

- A backend `hasActivity` value: not provided; the lists already answer it.
- Hiding 확인할 일 when it is empty: the Figma section is always there.

## Agent Guidance

- A new list on the owner home must join the first-visit check in
  `useOwnerHome`.
- Use `TodoStartCard` · `TodoNoneCard` for any other home that needs the
  same states.

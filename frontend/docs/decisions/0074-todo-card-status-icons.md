# 0074. Status icons in the 「확인할 일」 cards

## Status

Accepted. The 「확인할 일」 cards on the owner and student home show a small
picture of the next step at the right of the gray status box.

## Context

- Every 「확인할 일」 card had the same shape (title, badge, gray status box,
  full-width button), so the kind of work waiting was readable only from
  the status sentence.
- The work kind icon before the title (`WorkKindIcon`) tells a request
  from a proposal and stays as it is.

## Decision

- Owner (`TodoCarousel`), icon by card type:
  - 초안 도착 → `todoDraft` (document with a magnifier)
  - 수정안 도착 → `todoRevision` (document with a pencil)
  - 새 제안 → `todoProposal` (light bulb speech bubble)
  - 지원자 → `todoApplicants` (two students with a magnifier)
- Student (`StudentTodoCarousel`), icon by the action the student takes:
  - 초안 제출 → `todoSubmitDraft` (document with a paper plane)
  - 조건 확인 → `todoCheckTerms` (clipboard with a magnifier)
  - 수정안 제출 → `todoSubmitRevision` (speech bubble with a pencil)
- The icon is 44px. Both boxes have the same shape: two lines on the left
  (the status sentence and a gray detail line) and the icon at the right
  across both lines. The box fills the spare card height and centers its
  text, so a one-line detail does not leave a gap above the button. It is
  at least 88px tall (the status line and two detail lines), so the owner
  cards, where a detail can take two lines, and the student cards, whose
  details fit one line, are the same height.
- The student box's gray line is the deadline hint: 「마감 전에 초안을
  올려 주세요」 (초안 차례), the 최종 마감 date (수정 요청), and 「작업 조건을
  확인하고 시작해 주세요」 (제안 수락).
- Korean text in the box wraps between words (`word-break: keep-all`).
- The pictures are 96px transparent WebP files in `src/assets/icons`,
  registered in `AppImage`.

## Rationale

- The pictures show what is left to do (check, choose, submit), not a
  finished mark, so the card still reads as a task.
- Keeping the picture inside the status box leaves the title row and the
  button where they were.

## Alternatives Considered

- **Replacing `WorkKindIcon` with the status icon**: the title icon tells a
  request from a proposal, which the status icon does not.

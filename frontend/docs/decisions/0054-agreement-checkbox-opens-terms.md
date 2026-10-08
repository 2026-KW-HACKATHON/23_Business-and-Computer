# 0054. The agreement checkbox opens its terms first

## Status

Accepted. 「책임 약관과 취소·환불 기준에 동의해요 (필수)」 on 안전결제, 제안 수락,
and 작업 시작 opens 「책임 약관과 취소·환불 기준」 as a bottom sheet when it is
empty; 「동의해요」 in the sheet checks it.

## Context

- The owner's payment section (안전결제 and 제안 수락 · 의뢰서작성·결제) and the
  student's 작업 시작 (의뢰 and 제안) ask for the same agreement before money
  is held or work starts, but the terms behind it could not be read there.
- The signup 「이용약관 안내 (팝업)」 (Figma node 1198-277, `TermsSheet`) already
  holds the text; only part of it is about payment, cancelling, and results.
- Figma before this: no popup for the agreement. Added 「책임 약관과 취소·환불
  기준 (팝업)」 to owner section C (node 3535-5514) and student section E (node
  3535-9168), each opened as an overlay from the checkbox of 안전결제 · 제안 수락
  · 작업 시작.

## Decision

- **`AgreementCheckbox`** (`src/features/signup/components`, tone, checked,
  onChange) replaces the plain `Checkbox` in `PaymentSection`,
  `StudentWorkStartPage`, and `StudentProposalStartPage`.
  - Empty and tapped: the sheet opens and the box stays empty.
  - 「동의해요」 (role color) checks the box and closes the sheet; the dim area
    closes it without checking.
  - Checked and tapped: the box empties at once, without the sheet.
- **Sheet** (`BottomSheet` with the `TermsSheet` styles): title 「책임 약관과
  취소·환불 기준」, description 「결제하기 전에 꼭 확인해 주세요」 (owner) ·
  「작업을 시작하기 전에 꼭 확인해 주세요」 (student), and `AGREEMENT_SECTIONS`
  from the signup terms: 작업비는 이렇게 오가요 · 수정 요청 · 취소와 환불 ·
  결과물의 권리 · 서로 지키는 약속.

## Rationale

- Reading the terms is part of agreeing, and one shared text keeps signup,
  payment, and work start saying the same thing.
- Unchecking without a sheet keeps changing one's mind easy.

## Alternatives Considered

- A separate 「보기」 link beside the checkbox: easy to skip, and the label
  already names the terms.
- New terms text for payment: it would drift from the signup terms.

## Agent Guidance

- A new screen that asks for this agreement uses `AgreementCheckbox`.
- Change the terms in `src/features/signup/lib/terms.ts`; the sheet takes
  the sections by title.

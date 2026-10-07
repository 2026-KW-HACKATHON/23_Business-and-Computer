# 0047. Finished flows leave the browser history

## Status

Accepted. After 의뢰 등록, 제안 보내기, or a payment (맡기기 · 결제, 제안
수락), the flow's screens are gone from the browser history, so back cannot
reopen them and finish the same thing again. The payment 「확인」 goes home.
Updates ADR 0031 and ADR 0037.

## Context

- Each flow pushes its steps, and completion only replaced the last entry.
  Back from the destination reopened 2/3 · 3/3 (with their state) or the
  pay screens, where the user could send or pay again.
- Payments leave the app for KakaoPay and come back as a new page load
  (`KakaoPayResultPage`), so the flow's entries sit in an older document.
- The payment 「확인」 went to 내 활동 › 진행 중 or the proposal detail; the
  user wants home.
- Browsers count at most 50 history entries per tab (Chrome, Firefox);
  past that `history.length` stops growing.

## Decision

- **Flow table** (`src/lib/flowHistory.ts`): `requestNew` (/owner/requests/new
  … /new/3), `ownerPay` (applicants list, applicant profile, assign, pay of one
  request), `proposalAccept` (proposal detail and accept), `proposalNew`
  (/student/proposals/new … /new/4). The first path parameter (request or
  proposal id) is part of the flow key.
- **Start** (`useFlowHistoryScope` in `src/hooks/useFlowHistory.ts`, called
  once in `App`): when a push or replace enters a flow from outside it, the
  page load's id, the router's history index (`history.state.idx`),
  `history.length`, and whether the previous entry is an app screen go to
  sessionStorage. Back and reload (POP) never record.
- **Finish** (`useFinishFlow`): counts the steps since the start — by the
  router index in the same page load, by `history.length` after a new page
  load (skipped when either length is at the 50 cap) — then saves the
  destination in sessionStorage (5 s) and calls `history.go(-n)`:
  - previous entry is an app screen → back to it, then push the destination,
    which also drops the flow from the forward history;
  - otherwise → back to the flow's first entry and replace it.
  - The scope opens the saved destination on the next location change, on
    load, or on `pageshow` when the older page comes back from the
    back-forward cache. Without a start record it only replaces the current
    entry.
- **Uses**: 의뢰 등록 3/3 → 의뢰 등록 - 완료; 제안 보내기 4/4 → 제안 보내기
  완료; KakaoPay approval 「확인」 and 「이미 결제된 …」 → home (/owner). A
  payment that cannot be completed still goes to its status screen.

## Rationale

- Rewinding removes the finished screens instead of adding a guard to each
  of them, and back lands where the user started the flow.
- sessionStorage survives the KakaoPay round trip in the same tab.

## Alternatives Considered

- Guards on every step page that redirect when the flow is done: rejected,
  back would still walk through each step and bounce.
- Replacing between steps so a flow takes one entry: rejected, in-app back
  between steps would need custom targets on every step.

## Agent Guidance

- A new multi-step flow that ends in sending or paying adds its paths to the
  table in `src/lib/flowHistory.ts` and ends with `useFinishFlow()`.
- Single-screen actions (지원하기, 초안 제출, 취소) keep replacing the current
  entry; the screen before them already shows the new state.

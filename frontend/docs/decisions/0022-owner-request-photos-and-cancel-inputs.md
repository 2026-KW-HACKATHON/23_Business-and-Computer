# 0022. Owner request photos and cancel inputs follow the job API

## Status

Accepted.

## Context

The owner request screens are about to call the backend job API
(`POST /jobs`, `POST /jobs/{id}/cancel`). Two screens did not match it:

- Registering a request had no way to add reference photos, although the
  student and owner request views already show 「참고 자료」. The team asked
  the backend to accept photos on `POST /jobs`; the field and an upload
  purpose for requests do not exist yet.
- `POST /jobs/{id}/cancel` takes `cancelReason` and `messageToStudent`, both
  required (not blank, up to 5000 characters), for an open request and for a
  work in progress alike. Cancelling an open request was a confirm popup
  with no inputs, and 작업 취소 marked 「학생에게 남길 말」 as optional.

## Decision

- 의뢰 등록 2/3 (`src/pages/OwnerRequestContentPage.tsx`) has a 「참고 사진」
  field under 수정 횟수: optional, up to 5 photos, JPG / PNG / WEBP up to
  10 MB (the shared upload limits in `src/api/media.ts`). A photo that does
  not fit is left out and a red line under the field says why
  (`addRequestPhotos` in `src/features/owner/lib/newRequest.ts`).
- The picked files stay in `RequestContent.photos` (`File[]`) in the router
  state, so 「내용 고치기」 keeps them. 3/3 shows them as 「참고 사진」 tiles in
  the request card, and the registered request keeps their names as
  `attachments`. Uploading waits for the backend field.
- Picked photos show a preview right away: a 40 px thumbnail on each 2/3
  row and the photo inside each 3/3 tile (`AttachmentTiles` `srcs`), from
  object URLs made by `src/hooks/useObjectUrls.ts`. 수정 요청
  (`src/pages/OwnerRevisionPage.tsx`) keeps its picked photos as files too
  and shows a small thumbnail in each chip.
- Cancelling an open request is its own screen,
  `src/pages/OwnerRequestCancelPage.tsx` at /owner/requests/:id/cancel: the
  request, 「의뢰를 취소할까요?」, 취소 이유, 학생에게 남길 말, and the
  「취소 후에는 되돌릴 수 없다는 걸 확인했어요」 check. 「의뢰 취소하기」 stays
  disabled until both texts are filled (spaces only do not count) and the
  check is on; it then shows 「의뢰를 취소했어요」 and goes to 내 활동 (보낸
  의뢰).
- 작업 취소 (`src/pages/OwnerWorkCancelPage.tsx`) requires 학생에게 남길 말 too.
- Figma 「2. 사장님 화면」 and the Notion 「화면 상태 전환표」 · 「페이지 주소
  정리」 show the same fields and route.

## Rationale

- A screen like 작업 취소 fits two text areas better than a popup, and both
  cancel flows now look and behave the same.
- Keeping `File` objects instead of names lets the API work upload the same
  files without changing the screens.

## Alternatives Considered

- Sending fixed text for the open-request cancel: rejected, the student
  would get a message the owner never wrote.
- Hiding the photo field until the backend has it: rejected, the user asked
  for the field now and the backend change is already requested.

## Agent Guidance

- When `POST /jobs` accepts photos, upload `RequestContent.photos` with
  `uploadImage` before creating the request, as student signup does with its
  photo (ADR 0019), and send the returned URLs.
- Send the cancel texts trimmed; the backend trims them too.

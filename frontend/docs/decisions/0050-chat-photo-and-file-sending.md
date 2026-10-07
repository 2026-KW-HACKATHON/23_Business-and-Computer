# 0050. Sending photos and files in chat

## Status

Accepted. The owner and student chat rooms (ADR 0034) send one photo or file
at a time through the chat attachment API. The attachment format table moves
to `src/lib/attachmentFormats.ts`, shared with the student submission upload
(ADR 0032).

## Context

The backend (dev) has:

- POST /chat-rooms/{roomId}/attachments/uploads `{ type: IMAGE | FILE,
  fileName (≤ 255, no / \ or control characters), contentType (≤ 100), size
  (> 0) }` → 201 `{ uploadId, uploadUrl, uploadHeaders, uploadUrlExpiresAt }`.
  - `uploadUrl` is a presigned S3 PUT for 10 minutes on a private bucket.
    `uploadHeaders` holds the signed `content-type` and `x-amz-tagging:
    chat-upload=pending`; `content-length` is signed too, so the body must
    be the file of the given size.
  - A prepared upload can be sent as a message for 1 hour.
- POST /chat-rooms/{roomId}/messages/attachments `{ clientMessageId, type,
  uploadId }` → 201 (new) or 200 (the same request again) with the stored
  message. The same `clientMessageId` with another `uploadId` is 409
  CHAT_MESSAGE_409.
- Formats (extension and MIME must pair): photos jpg · jpeg · png · webp ·
  gif up to 10MB; files pdf · zip · doc · docx · xls · xlsx · ppt · pptx up
  to 50MB. HEIC photos and .hwp documents are not accepted. One file per
  request.
- Errors: CHAT_UPLOAD_400_TYPE (format, or type not matching the upload),
  CHAT_UPLOAD_400_SIZE, CHAT_UPLOAD_404 (no upload of this user in this
  room), CHAT_UPLOAD_409_NOT_READY (over 1 hour, or the object is missing or
  of another size), CHAT_UPLOAD_409_USED (attached to another message),
  CHAT_UPLOAD_502 (storage check or tagging failed), CHAT_MESSAGE_409, and
  the chat errors of ADR 0034.
- Figma 「채팅방 (사장님)」 (node 1455-1621) and 「채팅방 (학생)」 (node
  1697-684): a grey 「+」 circle left of the input, and a white bordered file
  bubble with a file icon, the file name, and a grey 「종류 · 크기」 line.

## Decision

- **Formats** (`src/lib/attachmentFormats.ts`): the extension → (IMAGE ·
  FILE, allowed MIME) table, `ATTACHMENT_MAX_BYTES` (10MB · 50MB),
  `ATTACHMENT_ACCEPT` for `input accept`, and `attachmentFormatOf`, which
  sends the extension's main MIME when the browser gives none or another
  one. The student submission (`isSubmittableFile`, `SUBMISSION_FILE_ACCEPT`,
  `sendSubmission`) uses the same table and behaves as before.
- **「+」 button**: a 40px `--role-muted` circle with 「+」 in
  `--role-secondary`, left of the input, wrapping a hidden file input with
  `accept={ATTACHMENT_ACCEPT}`. One file per pick; the input is cleared after
  each pick.
- **Check before sending** (`checkAttachment`): another format →
  「보낼 수 없는 형식이에요. 사진은 JPG·PNG·WEBP·GIF, 파일은 PDF·ZIP·워드·엑셀·
  파워포인트만 보낼 수 있어요」; an empty file → 「빈 파일은 보낼 수 없어요」; over
  the limit → 「사진은 10MB까지 보낼 수 있어요」 · 「파일은 50MB까지 보낼 수
  있어요」. Nothing is sent after the alert.
- **Sending** (`sendAttachment` in `useChatRoom`): a new `clientMessageId`,
  a bubble with 「보내는 중」 at once, then prepare →
  `fetch(uploadUrl, { method: "PUT", headers: uploadHeaders, body: file })`
  → POST messages/attachments. Success swaps in the stored message by
  `clientMessageId`, as for text.
- **Bubbles**: a photo being sent shows the picked file through
  `useObjectUrls`; a file shows the file icon, its name, and 「PDF · 2.1MB」
  (`attachmentDetailText`). Files sent from this screen keep the size line
  after they are stored; other files show the name only.
- **Failures** (`attachmentFailureOf`):
  - CHAT_UPLOAD_400_TYPE → alert 「보낼 수 없는 형식이에요」, CHAT_UPLOAD_400_SIZE
    → 「사진은 10MB, 파일은 50MB까지 보낼 수 있어요」, CHAT_UPLOAD_409_USED →
    「이미 다른 메시지로 보낸 파일이에요」; the bubble is removed.
  - Otherwise the bubble shows 「보내지 못했어요」, a reason line, and
    「다시 보내기」: CHAT_UPLOAD_404 「올린 파일을 찾지 못했어요」,
    CHAT_UPLOAD_409_NOT_READY 「올리기가 끝나지 않았거나 시간이 지났어요」,
    CHAT_MESSAGE_409 「같은 메시지로 다시 보낼 수 없어요」, CHAT_UPLOAD_502
    「파일 저장소에 연결하지 못했어요」; network or PUT failures have no reason
    line.
  - 401, CHAT_403, and CHAT_ROOM_404 leave the room as in ADR 0034.
- **다시 보내기**: when the PUT finished (an `uploadId` is kept), the message
  is sent again with the same `clientMessageId`. When the failure came before
  that, or was CHAT_UPLOAD_404, CHAT_UPLOAD_409_NOT_READY, or
  CHAT_MESSAGE_409, the bubble takes a new `clientMessageId` and starts again
  from prepare.

## Rationale

- Checking format and size first avoids an upload the server rejects.
- Keeping the `uploadId` after a finished PUT resends without uploading the
  file again; a new `clientMessageId` for a new upload avoids
  CHAT_MESSAGE_409.
- One format table keeps submission and chat on the backend's single rule.

## Alternatives Considered

- `XMLHttpRequest` for an upload progress bar: not used; the bubble shows
  「보내는 중」 only.
- Several files per pick: not used; one pick is one message.

## Agent Guidance

- The PUT needs the private chat bucket's CORS to allow PUT from the
  frontend origin with the `content-type` and `x-amz-tagging` headers. A PUT
  that CORS blocks shows as a failed bubble with no reason line.

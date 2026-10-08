# 0062. Other users see the uploaded profile photo

## Status

Accepted. Wherever a screen shows the other party (the store to a student,
the student to an owner), the small round photo shows the photo that person
uploaded, and falls back to the role icon when there is none.

## Context

- The user reported that a profile photo uploaded in 내 정보 is never seen by
  anyone else. Every other-party photo was `RoleAvatar`, which only drew the
  role icon.
- Some responses already carried the photo:
  - `counterpartProfileImageUrl` on GET /me/chat-rooms and
    GET /chat-rooms/{roomId};
  - `profileImageUrl` on the applicants of GET /jobs/{id}/applications;
  - `profileImageUrl` on GET /explore/stores and on the store of
    GET /me/proposals.
- Others did not, and the backend adds a nullable field (null when there is
  no photo):
  - GET /jobs/{jobId}: `storeProfileImageUrl`;
  - GET /proposals/{id}: `storeProfileImageUrl` and `student.profileImageUrl`;
  - GET /me/received-proposals: `student.profileImageUrl`;
  - GET /me/jobs?status=MATCHED: `studentProfileImageUrl` (owner) and
    `storeProfileImageUrl` (student);
  - GET /jobs/{id}/applications/{appId}/profile and
    GET /students/{id}/profile: `student.profileImageUrl`.

## Decision

- `RoleAvatar` takes `src`. An `https://` address is drawn as an `<img>`
  filling the circle (`object-fit: cover`, the yellow ring stays); no address,
  another scheme, or a load error shows the role icon. A new address is tried
  again.
- `StoreBox`, `StudentBox`, and the activity lines take `photo`; `ChatRow`
  takes `partnerPhoto`.
- Places and sources:
  - chat list and chat room (header and each message from the other party):
    the room's `counterpartProfileImageUrl`;
  - 지원자 목록, 맡기기, 안전결제: the applicant's `profileImageUrl`;
  - 가게 목록 and 제안 보내기 1/4 가게 고르기: the store's `profileImageUrl`
    (kept on `ExploreStore.photo`, so the later steps carry it too);
  - 의뢰서 상세 (student, owner 탐색): `storeProfileImageUrl`;
  - 제안 상세 (student and owner, 탐색 too): `storeProfileImageUrl` and
    `student.profileImageUrl`;
  - 학생 프로필 (80px): `student.profileImageUrl`;
  - 사장님 내 활동: 받은 제안 `student.profileImageUrl`, 진행 중
    `studentProfileImageUrl`, else the received proposal's student;
  - 보낸 의뢰 상세 맡은 학생: the matched list, else the chat room's
    `counterpartProfileImageUrl`;
  - 학생 내 활동: 보낸 제안 the store's `profileImageUrl`, 진행 중
    `storeProfileImageUrl`, else the job detail, else the sent proposal's
    store.
- The new fields are optional in the frontend types, so until the backend
  sends them those places keep the role icon.

## Rationale

- One component change covers all places, and the icon fallback keeps the
  current look for users without a photo and for old servers.
- Falling back to responses the screen already loads gives the photo where
  the main response does not have it yet, without extra requests.

## Alternatives Considered

- A separate request per person for the photo: more requests for a small
  picture, and no such endpoint exists.
- Showing a blank circle while the photo loads or after it fails: the role
  icon tells the role and matches the current design.

## Agent Guidance

- A new place that shows the other party uses `RoleAvatar` with `src` (or the
  `photo` prop of the box components) from the response, not the bare icon.
- Keep the `https://` check; upload previews (`blob:`) are only for the
  uploader's own `ProfilePhoto`.

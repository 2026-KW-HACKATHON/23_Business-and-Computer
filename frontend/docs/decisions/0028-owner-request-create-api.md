# 0028. Owner 의뢰 등록 calls POST /jobs

## Status

Accepted. 의뢰 등록 3/3 creates the request on the backend. The owner's
request list, detail, applicants, and selection are wired in ADR 0030 and
ADR 0037. 1/3 picks from GET /specialties and carries the ids (ADR 0058).

## Context

- 의뢰 등록 1/3 → 3/3 (ADR 0013, ADR 0022) only added the request to the
  sample list in the browser.
- The backend (dev) has POST /jobs with `{ specialtyIds (1 or more, positive),
  title (≤255), description, budget (positive), draftDeadline, finalDeadline
  (today or later, draft ≤ final), revisionCount (0 or more),
  referenceImageUrls (up to 4, no duplicates) }`. It answers with no data
  (no job id).
  - Only an owner with a profile may call it: 403 OWNER_403 otherwise.
  - The specialty ids are checked: 400 SPECIALTY_400 /
    SPECIALTY_400_DUPLICATE.
  - Photo URLs must come from POST /media/images/uploads with purpose JOB
    for the same user and be uploaded: 400 JOB_400_IMAGE_URL,
    409 JOB_409_IMAGE_NOT_UPLOADED.

## Decision

- **API**: `createJob` in `src/features/owner/api/jobApi.ts`.
  `toJobCreateRequest`, `uploadRequestPhoto`, and `sendJobCreate` in
  `src/features/owner/lib/newRequest.ts`. `ImagePurpose`
  (`src/api/media.ts`) gains `JOB`.
- **Specialty ids**: the ids picked on 1/3 (ADR 0058). SPECIALTY_400 /
  SPECIALTY_400_DUPLICATE show 「할 일 목록이 바뀌었어요. 다시 골라 주세요」
  and open 1/3 with the picks cleared.
- **Photos**: up to 4 (`MAX_REQUEST_PHOTOS`, was 5). Each picked photo is
  uploaded with purpose JOB before POST /jobs; a retry reuses the URLs already
  uploaded, and a JOB_400_IMAGE_URL / JOB_409_IMAGE_NOT_UPLOADED answer drops
  them so the next press uploads again.
- **3/3 button** (`src/pages/OwnerRequestConfirmPage.tsx`): 「등록하는 중...」
  while sending; a double press sends once; an answer after leaving the
  screen is dropped. Errors above the button: photo upload 「사진을 올리지
  못했어요. 다시 시도해 주세요」, other 400 「입력한 내용을 다시 확인해
  주세요」, other 409 「일시적인 문제가 생겼어요…」, anything else 「잠시 후
  다시 시도해 주세요」. 401 goes to /login; OWNER_403 shows 「사장님만 의뢰를
  등록할 수 있어요」 and then `landingPath()`.
- **After success**: the done screen as before; 내 활동 and the home read
  the new request from GET /me/jobs?status=OPEN (ADR 0030).

## Rationale

- Sending server ids follows ADR 0021.
- The upload order and retry rules match the student proposal (ADR 0020), so
  both flows behave the same.

## Alternatives Considered

- Looking up the ids by name at send time: replaced by picking from
  GET /specialties on 1/3 (ADR 0058).

## Agent Guidance

- The sent-request list, detail, applicants, and cancel are in ADR 0030.

# 0028. Owner 의뢰 등록 calls POST /jobs

## Status

Accepted. 의뢰 등록 3/3 now creates the request on the backend. The owner's
request list, detail, applicants, and selection still read the sample data of
ADR 0017 and are wired in later steps.

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
- 1/3 keeps the picked work as names (field + task from
  `SPECIALTY_BADGES`), not server ids.

## Decision

- **API**: `createJob` in `src/features/owner/api/jobApi.ts`.
  `requestSpecialtyIds`, `toJobCreateRequest`, `uploadRequestPhoto`, and
  `sendJobCreate` in `src/features/owner/lib/newRequest.ts`. `ImagePurpose`
  (`src/api/media.ts`) gains `JOB`.
- **Specialty ids**: 「의뢰 등록하기」 first reads GET /specialties
  (`fetchSpecialties`). Each picked task is found by field name and task name
  (`findSpecialtyByName`); a picked field with no task (「기타」) uses the
  one specialty of that category (`implicitSpecialty`). If any name is not on
  the server, 「할 일 목록이 바뀌었어요. 다시 골라 주세요」 and 1/3 opens with
  nothing picked.
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
- **After success**: the done screen as before. Until the owner request list
  reads GET /me/jobs, the request is also added to the sample list
  (`registerOwnerRequest`) so 내 활동 and the home show it.

## Rationale

- Reading the specialty list at send time keeps 1/3 unchanged and still
  sends server ids, as ADR 0021 asks.
- The upload order and retry rules match the student proposal (ADR 0020), so
  both flows behave the same.

## Alternatives Considered

- Switching 1/3 to the server specialty list now: rejected for this step; the
  names already match the server, and the step is about sending.

## Agent Guidance

- When the owner request list reads GET /me/jobs, remove the
  `registerOwnerRequest` call after a successful POST /jobs.

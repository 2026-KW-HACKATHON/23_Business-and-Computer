# 0020. Student 「제안 보내기」 calls the backend API

## Status

Accepted. Replaces the sample stores, the `SPECIALTY_BADGES` task list, and
the in-memory send of the new-proposal flow in ADR 0018.

## Context

The student new-proposal flow (/student/proposals/new → 2 → 3 → 4 → done) ran
on sample data: stores from `sampleStores.ts`, tasks by name from
`SPECIALTY_BADGES`, photos kept as names only, and 「제안 보내기」 added the
proposal to the demo store. The backend (dev) has:

- `POST /proposals` → 201 `{ data: { proposalId } }`. Body:
  `ownerProfileId` (required), `specialtyIds` (**at least one**, no duplicates,
  must exist), `title` (≤ 255), `customerProblem` / `proposedSolution` /
  `workPlan` (each ≤ 500, not blank, trimmed by the server), `proposedFee`
  (> 0), `draftDays` / `finalDays` (≥ 0, final ≥ draft), and optional
  `referenceImageUrls`. Only an active STUDENT with a profile may send.
  Errors: 401 `COMMON_401`, 403 `PROPOSAL_403_STUDENT`, 404 `OWNER_404`,
  400 `SPECIALTY_400` / `SPECIALTY_400_DUPLICATE`, 400
  `PROPOSAL_400_IMAGE_URL` (not a PROPOSAL URL issued to this student), 409
  `PROPOSAL_409_IMAGE_NOT_UPLOADED`, 502 `MEDIA_UPLOAD_502`, 400
  `COMMON_400`, 409 `COMMON_409`, 500 `COMMON_500`.
- `GET /explore/stores?sort&businessCategoryId&size(≤100)&cursor` (students
  only) → `{ items: [{ storeName, profileImageUrl, businessCategory: { id,
  name }, storeAddress, ownerProfileId, createdAt }], nextCursor, hasNext }`.
  There is no name search, no business-category list API, and no single-store
  API.
- `GET /specialties` gives categories with ids; 「기타」 comes with
  `specialties: []`.
- `POST /media/images/uploads` with `purpose: "PROPOSAL"`, then an S3 `PUT`.

The team lead is building shared API helpers (a tokens module under src/api,
`apiData` and `authHeaders` in `src/api/client.ts`, and `uploadImage` in a
new media module under src/api). This change must not touch `src/api/client.ts`, the auth
token files, or the signup photo upload, to avoid conflicts with that PR.

## Decision

- **Specialties are a shared feature**: `src/features/specialty` holds
  `fetchSpecialties` (moved from signup), `useSpecialties` (loading / error /
  `reload`, late responses dropped), `selectableCategories` (drops categories
  with no specialty), and `findSpecialtyByName`. Signup step 3 now uses it;
  its behavior is unchanged (ADR 0019).
- **Temporary request helpers**: every proposal and store call goes through
  two small functions in `src/features/student/api/request.ts` —
  `requestData<T>` (adds the stored token, returns `data`) and
  `uploadImageAsProposal` (prepare with purpose PROPOSAL, then S3 `PUT`). For
  now they copy the signup approach (`getAccessToken` + `apiFetch`, the same
  upload steps). `src/features/specialty/api/specialtyApi.ts` has the same
  `requestData`.
- **1/4 and 가게 탐색** (`StudentStoresPage`) use `useExploreStores`, which
  loads every page of `GET /explore/stores` (`sort=OLDEST` for 「등록순」,
  `size=100`, at most 20 pages). Category chips (fixed 11 from Figma) and the
  name search filter on the client by the response's category name. Loading
  and failure show `LoadNotice` (「다시 시도」). The chosen store travels as
  `{ ownerProfileId, name, category, address }` in router state, since no API
  returns one store. 「제안하기」 in 가게 탐색 passes that object to 2/4.
- **2/4** draws categories and tasks from `useSpecialties`, keeping only
  categories that have tasks, so 「기타」 is hidden and at least one task is
  required (the server needs a specialty id). Picks are stored as
  `{ specialtyId, name, categoryId, categoryName }`. Field icons and hints
  are looked up by category name; an unknown name gets the 「전체」 icon and no
  hint. A home example preselects its task only if a category and task with
  the same names exist on the server.
- **3/4** keeps photos as `File` objects in router state (structured clone).
  Only jpeg/png/webp up to 10 MB, at most 5 photos; a refused file shows
  「JPG, PNG, WEBP 사진만 올릴 수 있어요」 or 「10MB 이하 사진만 올릴 수
  있어요」. The draft days still need at least 1 (the server allows 0).
- **4/4** 「제안 보내기」 uploads the photos one by one, then sends
  `POST /proposals`. The button reads 「보내는 중...」; an `inFlight` ref
  blocks double sends and a `requestId` ref drops responses after leaving.
  Photos already uploaded are reused on retry. Results:
  - sent → done screen (history replaced);
  - 401 → /login;
  - `PROPOSAL_403_STUDENT` → alert 「학생만 제안을 보낼 수 있어요」, then
    `landingPath()`;
  - `OWNER_404` → alert 「가게 정보가 바뀌었어요. 가게를 다시 골라 주세요」,
    back to 1/4 with the store cleared;
  - `SPECIALTY_400*` → alert 「할 일 목록이 바뀌었어요. 다시 골라 주세요」,
    back to 2/4 with picks cleared;
  - a photo upload failure, `PROPOSAL_400_IMAGE_URL`, or
    `PROPOSAL_409_IMAGE_NOT_UPLOADED` → 「사진을 올리지 못했어요. 다시 시도해
    주세요」 (the image errors also drop the cached URLs so a retry uploads
    again);
  - other 400 → 「입력한 내용을 다시 확인해 주세요」; other 409 → 「일시적인
    문제가 생겼어요. 다시 시도해도 안 되면 문의해 주세요」; 5xx (including
    `MEDIA_UPLOAD_502`) or network → 「잠시 후 다시 시도해 주세요」. These
    follow ADR 0019's rule.
- The 4/4 category badges show only names that match a `Field`.

## Rationale

- Loading every store page keeps the name search complete; a 월계1동 store
  list is small.
- Uploading at send time avoids expired upload URLs and orphan photos when the
  student leaves before sending.
- Keeping the token and upload code in one tiny function per feature makes
  the switch to the shared helpers a one-line change.

## Alternatives Considered

- Showing 「기타」 with no tasks: rejected, the server needs at least one
  specialty id.
- Infinite scroll for stores: rejected for now; the name search would only see
  loaded pages.
- Uploading photos as soon as they are picked: rejected, see Rationale.
- Creating a separate media feature for uploads: dropped, the team lead's
  shared media module will cover it.

## Agent Guidance

- **After the team lead's shared API is merged**, replace:
  - `requestData` in `src/features/student/api/request.ts` and
    `src/features/specialty/api/specialtyApi.ts` with `apiData<T>(path, init)`
    (it also retries 401 once through `POST /refresh`);
  - `uploadImageAsProposal` with `uploadImage(file, "PROPOSAL")`.
  Keep the function names so callers do not change, or inline them and delete
  the file.
- The sent-proposal list (내 활동 › 보낸 제안) and the proposal detail still
  use sample data, so a proposal sent through the API does not show there
  yet. Wire `GET /me/proposals` and `GET /proposals/{proposalId}` next.
- Other screens still use `SPECIALTY_BADGES` (request writing, profile edit,
  sample data); move them to `useSpecialties` in the follow-up issue.

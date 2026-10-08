# 0063. Portfolio export picks results and saves one ZIP

## Status

Accepted. 「포트폴리오 내보내기」 on 내 작업물 모아보기 opens a sheet where the
student picks completed works one by one or with 「전체 선택」, and saves the
final result files of the picked works as one ZIP.

## Context

- The screen only had a confirm popup; packing files waited for the backend.
- The backend added `POST /jobs/submissions/download`:
  - body `{ jobs: [{ jobId, fileUrls }] }` (no limit on jobs or files; job
    ids and file URLs must not repeat);
  - the owner or the assigned student of each job may call it, whatever the
    job status;
  - success is a ZIP stream named `work-submissions.zip`;
    errors before the stream starts are the usual JSON envelope:
    `JOB_SUBMISSION_400_FILE_URL` (not this job's file), 404
    `JOB_SUBMISSION_404_FILE` (missing in storage), 502 `JOB_SUBMISSION_502`,
    503 `JOB_SUBMISSION_503_DOWNLOAD` (too many downloads at once).
- The user asked to export only the chosen results, and to export everything
  with a 「전체 선택」 control.

## Decision

- **API**: `apiFile(path, init)` in `src/api/client.ts` returns the response
  as a `Blob`. Errors and the 401 refresh work like `apiData` (both use
  `withSession`). `src/api/submissionDownload.ts` has
  `downloadSubmissionZip(jobs)` (failure reasons: unauthorized, busy,
  missing, failed) and `saveFile(blob, name)`, which saves through a
  temporary link.
- **Sheet** (`BottomSheet`, title 「포트폴리오 내보내기」, 「ZIP으로 저장할
  결과물을 골라 주세요」):
  - 「전체 선택 (N건)」 checkbox, a line, then one checkbox per completed work
    whose final files loaded: title, store · date · 「파일 n개」. Nothing is
    picked at first; 「전체 선택」 picks or clears all and turns on when every
    work is picked;
  - the button reads 「N건 ZIP으로 저장하기」, or 「ZIP으로 저장하기」 disabled
    when nothing is picked. While the ZIP is made it shows the button loading
    dots (ADR 0059);
  - success saves 「골목인턴_포트폴리오_YYYY-MM-DD.zip」, closes the sheet,
    and clears the picks. 401 goes to /login. Other failures keep the sheet
    open with a red line above the button: busy 「지금 내려받는 사람이
    많아요. 잠시 후 다시 시도해 주세요」, missing 「찾을 수 없는 파일이
    있어요. 목록을 새로 고친 뒤 다시 골라 주세요」, others 「ZIP을 만들지
    못했어요. 잠시 후 다시 시도해 주세요」;
  - when no work has files, the sheet says 「내보낼 결과물 파일이 없어요」.

## Rationale

- Picking whole works matches how the list is shown (one row per work) and
  sends every final file of a picked work, which is what a portfolio needs.
- A sheet keeps the list behind it and gives room for many works.

## Alternatives Considered

- Picking single files inside a work: more taps for little gain; a work's
  final files belong together.
- A selection mode on the list itself: the rows already open 내 결과물.

## Agent Guidance

- File downloads from the backend use `apiFile`, not `fetch`.
- Sample (demo) results use placeholder URLs, which the download API refuses
  as not this job's file; the sheet then shows the missing line.

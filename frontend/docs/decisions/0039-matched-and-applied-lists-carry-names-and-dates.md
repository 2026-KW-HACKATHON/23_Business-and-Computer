# 0039. Matched and applied lists carry names, fees, and dates

## Status

Accepted. The owner and student in-progress lists read the student name,
fee, revision count, store name, and submission time from the list itself,
so most screens make fewer requests. Arrival and submission dates replace
「7일 동안」 where Figma shows a date. The student applied list shows the store,
the sent application, and not-selected results with no code change. Updates
ADR 0027, ADR 0032, ADR 0035, and ADR 0038.

## Context

Backend PR #201 added:

- GET /me/jobs?status=MATCHED (owner): `studentName`, `budget`,
  `revisionCount`, and, for the pending submission, `revisionNumber` and
  `submittedAt`. The application is still only in GET /me/chat-rooms.
- GET /me/jobs?status=MATCHED (student): `storeName` and the latest
  submission's `submittedAt`. The store address is still only in GET
  /jobs/{id}.
- GET /me/job-applications: `storeName`, `summary`, `workPlan`,
  `deliveryMethod`, and REJECTED items (applications to jobs that chose
  another student).
- GET /me/received-proposals: `jobStatus` and `createdAt` (ADR 0025 already
  reads them).

`submittedAt`, `appliedAt`, and the latest submission times are UTC without
an offset; the received proposal `createdAt` is Korean time.

## Decision

- **Dates**: `koreaDate` (`src/lib/date.ts`) turns a server time (+09:00)
  into the Korean date. The automatic completion date is that date + 7 days.
- **Owner data** (`src/features/owner/lib/progressJobs.ts`):
  `loadOwnerProgressJobs` reads the name, fee, revision count, and arrival
  date (`arrivedOn`) from the list and only GET /me/received-proposals
  besides (kind and the name fallback). GET /me/chat-rooms is gone from the
  load.
- **Application**: the application of a request comes from GET
  /me/chat-rooms when 보낸 의뢰 opens on a working job (`loadAssignedWork`,
  ADR 0049).
- **Owner dates**: the home 「확인할 일」 card reads 「M월 D일까지 확인하지 않으면
  자동으로 완료돼요」, and 작업 확인 reads 「학생 · 초안 도착 M월 D일 · 수정
  n/m」 with 「M월 D일까지 확인해 주세요」 / 「답이 없으면 자동으로 완료돼요」.
  Without a date they keep 「7일 동안」.
- **Student data** (`src/features/student/lib/progressJobs.ts`): the store
  name and `submittedOn` come from the list. Only 내 활동
  (`useProgressJobs({ storeAddress: true })`) still calls GET /jobs/{id} per
  job, for the store address on its cards.
- **Student dates**: the home 「사장님이 확인 중」 row reads 「초안 제출 : M월
  D일」; 제출한 초안 · 수정안 reads 「가게 · M월 D일 제출 · 수정 n/m」 and
  「M월 D일까지 답이 없으면 자동으로 완료돼요」.
- **Applied list**: the cards and the sheet already handled `storeName`, the
  application, and REJECTED (ADR 0027). 「M월 D일 지원할 때 보냄」 now reads
  `appliedAt` as UTC.

## Rationale

- Loading the application only on tap removes GET /me/chat-rooms from the
  home, 작업 확인, 수정 요청, and 작업 취소, which never show it.
- The store address appears only on 내 활동 cards, so the per-job GET
  /jobs/{id} stays there and leaves the home and the work screens.

## Alternatives Considered

- Dropping the store address from 내 활동 cards: rejected, the Figma card
  shows it.
- Keeping GET /me/chat-rooms in the load for the sheet: rejected, every
  screen would pay for a sheet that only two screens open.

## Agent Guidance

- When the student matched list carries the store address, drop the
  `storeAddress` option and GET /jobs/{id} from `loadProgressJobs`.
- When the owner matched list carries the application, read it there and
  drop GET /me/chat-rooms from `loadAssignedWork` (ADR 0049).
- Server times carry +09:00; read their dates with `koreaDate`.

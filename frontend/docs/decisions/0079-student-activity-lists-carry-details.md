# 0079. Student 내 활동 reads store address, fields and ratings from the lists

## Status

Accepted. The student 내 활동 screen no longer asks for each job's details
or review. `GET /me/jobs?status=MATCHED` carries `storeAddress`, and each
`GET /settlements` row carries `specialtyCategories` and the received
`rating`. `GET /settlements` joins the tab list cache (ADR 0077).

## Context

- 내 활동 sent 14 requests for 3 진행 중 and 3 완료 jobs: the 4 lists,
  `GET /settlements` twice (완료 tab and 정산 summary), `GET /jobs/{id}` for every
  진행 중 job (only for the store address), and `GET /jobs/{id}` plus
  `GET /jobs/{id}/review` for every 완료 job (only for the field badges and
  the star rating). The per-job calls waited for `GET /settlements`, so the 완료
  tab filled in one round trip later, and the count grew with every job.
- 내 작업물 모아보기 asked `GET /jobs/{id}/review` per completed job for the
  same rating.
- The backend already reads the owner profile for the matched list, and the
  job specialties and reviews can be read for many jobs in one query each.

## Decision

- Backend: the student matched list adds `storeAddress` from the owner
  profile it already reads. `GET /settlements` adds `specialtyCategories`
  (empty list when none) and `rating` (left out when there is no review) to
  every row. `PaymentFacade.getSettlementHistoryWithJobDetails` reads the job
  specialties and the student's reviews for all rows with one `IN` query
  each. 홈 and `GET /students/me` keep using `getSettlementHistory`, so they
  do not pay for these queries, and their settlement rows leave the two
  fields out (`NON_NULL`).
- Frontend: `loadProgressJobs` takes the address from the list (the
  `storeAddress` option is gone), and `loadFinishedJobs` takes the fields and
  rating from the settlement row (the `details` and `reviews` options are
  gone). Only `files` still asks `GET /jobs/{id}/result` per completed job,
  because the file list exists only there.
- `GET /settlements` is added to the cache allowlist. 내 활동 asks it from two
  hooks at once, and the in-flight sharing sends it once. Its rows change
  only through writes (cleared on every write) or the owner's actions, which
  also send the student a notification and refresh remembered lists.

## Rationale

- 내 활동 drops from 14 to 4 requests and stays at 4 however many jobs the
  student has; 내 작업물 모아보기 drops from 8 to 5.
- Adding fields keeps every existing client and response working.
- Caching `GET /settlements` follows the same staleness rules as the other tab
  lists, so no screen-specific sharing code is needed.

## Alternatives Considered

- **Share the in-flight `GET /settlements` request without caching it**: a
  second mechanism next to the cache for one path, and 내 활동 would still
  wait for the network on every visit.
- **A new endpoint for the whole 내 활동 screen**: fewer requests still, but
  it duplicates four list contracts that other screens use.
- **Enrich the settlement rows for every caller**: 홈 loads on every tab
  switch and would pay for two more queries it does not use.

## Agent Guidance

- New 내 활동 or 포트폴리오 data that exists per job should come with the list
  rows (one `IN` query on the backend), not from `GET /jobs/{id}` per row.
- `GET /jobs/{id}/review` stays for the review detail screen
  (`loadFinishedWork`), which needs the full review text.

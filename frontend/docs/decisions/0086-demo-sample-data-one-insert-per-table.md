# 0086. Demo sample data goes in with one insert per table

## Status

Accepted. A new demo pair (`POST /demo/login` without `demoSessionId`, ADR
0024) now writes its sample data with one multi-row `INSERT` per table. The
ids of the tables whose id the database makes are taken from the table's own
sequence in one query first. This is the server side that ADR 0081 left for
later.

## Context

- On the deployed site `POST /demo/login` for a new pair took about 1.26 s
  (ADR 0081). Locally, with 3 ms added to every database reply (`tc netem` on
  the PostgreSQL container, which matched the deployed time before), it took
  0.91 s. Almost all of it was waiting for database round trips.
- `DemoSampleDataSeeder` already built the sample entities directly instead
  of going through the services, and sent specialties, likes, certificates,
  notifications and chat messages as multi-row inserts. But every entity
  table uses `GenerationType.IDENTITY`. Hibernate must send such an insert at
  once to learn the id, so 33 jobs, 24 applications, 23 payments, 19
  submissions, 12 reviews, 10 proposals, 9 sample accounts and their profiles
  each cost one round trip, and the state changes and past times were sent as
  updates afterwards.
- The deployed `DATABASE_URL` is a secret, so a driver option such as
  `reWriteBatchedInserts` cannot be relied on, and it would not help
  IDENTITY inserts anyway.

## Decision

- The seeder keeps building entities with the same factories and state
  methods (`Job.create`, `match`, `approve`, `requestRevision`, `complete`,
  `refundOnCancel`, …) but does not hand them to Hibernate. `stage(entity)`
  collects them per class.
- For jobs, applications, payments, submissions, reviews, proposals, store
  concerns, owner profiles and student profiles, the first `stage` takes ids
  from each table's identity sequence in one query
  (`nextval(pg_get_serial_sequence(table, 'id'))` with `generate_series`).
  The counts in `ID_TABLES` are the current sample row counts; if a table
  runs out, only that table gets more. The id is set on the entity right
  away, so the other sample rows and the notifications use it as before.
- Past times (written, applied, paid, submitted, revision requested,
  completed, reviewed, cancelled, refunded) are kept per entity and column
  (`backdate`) and go into the insert itself, instead of updates after it.
- `finish` inserts each table with one `INSERT … VALUES (…), (…)`; the
  columns are listed next to the entity getters (`EntityColumns`). Chat rooms
  go in after the chat messages, so their last-read message ids are part of
  the insert. jsonb columns are written with the application `ObjectMapper`.
- The sample accounts (`users`) and their owner and student profiles are made
  the same way, as `UserService.createDemoSampleUser`,
  `OwnerService.createOwnerProfile` and `StudentService.createStudentProfile`
  would make them. The visitor pair in `DemoFacade` still goes through the
  services.
- The JDBC batch size in the seeder and `hibernate.order_updates` in
  `application.yaml`, which only existed for the old seeder updates, are
  removed.

## Results

Local, one new pair, after warm-up, median of 7:

| | before | after |
| --- | --- | --- |
| SQL statements in the transaction | 294 | 46 |
| database round trips (PostgreSQL replies) | 236 | 47 |
| `POST /demo/login`, no added delay | 0.30 s | 0.08 s |
| `POST /demo/login`, 3 ms per reply | 0.91 s | 0.20 s |

The data is the same: for one new pair made before and after, all 527
screen API answers of the owner and the student match after replacing ids
and session ULIDs, except the times taken from 「now」, and 18 tables
(users, profiles, jobs, applications, payments, submissions, reviews, chat
rooms and messages, proposals, likes, specialties, certificates, store
concerns, notifications) have the same content hashes.

## Rationale

- Taking ids from the same sequence the identity column uses keeps them
  unique against rows other requests insert at the same time, and lets the
  seeder keep its order (a payment needs the job and application ids, a
  notification needs the target id) without a second pass.
- Inserting the final state once is simpler for the database than insert
  plus update, and there is only one pending submission per job in the final
  state, so `job_submissions_one_pending_per_job_key` holds without the flush
  the old seeder needed.
- There are no physical foreign keys (backend persistence rules), so the
  tables can go in in any order.

## Alternatives Considered

- **`INSERT … RETURNING id` in dependency order at the end**: also one round
  trip per table, but every id would be unknown while building, so maps,
  child rows and notifications would all need a second pass.
- **Changing the entities to a sequence generator with batching**: changes
  the id mapping of the real service for a demo-only speed-up.
- **A pool of ready demo pairs**: see ADR 0081.

## Agent Guidance

- When a sample entity gets a new column, add it to the table's
  `EntityColumns` in `DemoSampleDataSeeder.insertEntities`; Hibernate does
  not write these rows. When the number of sample rows of a table changes,
  update its count in `ID_TABLES` (too few costs one extra query, too many
  leaves unused ids).
- After changing the seeder, compare a new pair made before and after (screen
  API answers and table hashes), as in the results above.
- Remaining per-pair round trips: the visitor pair in `DemoFacade` (accounts,
  profiles, specialties through the services) and one student number check
  per sample student (`DemoStudentNumbers`).

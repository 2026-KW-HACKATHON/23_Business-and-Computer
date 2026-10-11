# 0084. Student MY reads a short 내 정보 summary

## Status

Accepted. The student MY screen and the profile edit screen read
`GET /students/me/summary` instead of `GET /students/me`. The 프로필 수정
screen keeps `GET /students/me`, whose answer does not change.

## Context

- Measured on the deployed site: MY waited for `GET /students/me` (median
  0.21 s, up to 0.43 s) while its other three calls took 0.15–0.16 s.
- `GET /students/me` (ADR 0041) also builds every received review (with the
  job, store and job specialties of each), the latest three settlements (with
  their jobs and applications), the proposal count, the review count and the
  average rating: 16 SQL statements per call.
- MY shows only the photo, name, school line, specialty categories, the
  verified badge and the 완료 count. Changing the photo on MY saves with
  `PUT /students/me`, which replaces everything, so MY also needs the
  introduction, portfolio URL, specialty ids and certificates. The profile
  edit screen needs the same fields and nothing else.

## Decision

- Backend: `GET /students/me/summary` in the existing `StudentController` and
  `StudentFacade` (`getMeSummary`). It answers 11 fields with the same names,
  values and shapes as in `GET /students/me`: `studentProfileId`,
  `profileImageUrl`, `name`, `university`, `studentNumber` (admission year),
  `major`, `introduction`, `portfolioUrl`, `completedJobCount`,
  `specialtyCategories`, `certificates`. Same errors: 401, 403
  `STUDENT_403_ME` for non-students, 500 without a student profile.
  `GET /students/me` is unchanged.
- Frontend: `StudentMeSummary` (the summary answer; `StudentMe` extends it),
  `useStudentMeSummary()` beside `useStudentMe()` in
  `src/features/student/hooks/useStudentMe.ts`. `useStudentPhotoChange`,
  `studentMeChanges` and `specialtyIdsOf` take the summary, which both answers
  satisfy.
- The summary path joins the tab list cache allow list
  (`src/api/cache.ts`, ADR 0077) next to `GET /students/me`. Any write, including
  the profile save and the photo change, clears the cache, so MY shows the
  saved profile right after returning.

## Results

Local measurement (jar with Postgres in Docker, one demo student):

| | GET /students/me | GET /students/me/summary |
| --- | --- | --- |
| SQL statements per call | 16 | 7 |
| Server time, median of 40 (curl) | 25.0 ms | 13.6 ms |
| Answer size | 2,512 bytes | 734 bytes |
| MY in the production build, 5 loads | 34–99 ms | 26–35 ms |

MY's text is the same before and after. On the deployed API each statement
costs a round trip to the database, so the call should come down to about
the 0.15 s of MY's other calls (to be checked after deploy).

## Alternatives Considered

- **A query parameter on `GET /students/me` (e.g. `?view=summary`)**: one
  path with two answer shapes, and the cache and types would have to tell
  them apart.
- **Make `GET /students/me` itself lighter**: the 프로필 수정 screen needs
  the reviews and settlements, so they would move to more calls there.
- **Take the 완료 count from the activity lists**: MY still needs the
  profile, and the closed jobs list is not one of MY's calls.

# 0041. Student 내 정보, 프로필 수정 · 편집, and 정산 내역 call the backend

## Status

Accepted. 내 정보 (profile, 완료 count, and changing the photo), 프로필 수정,
프로필 편집, 정산 내역, and the settlement summary above 내 활동 › 완료 read
and write the backend. The sample profile, the demo profile and photo state,
and the sample settlements are gone. Updates ADR 0018 and ADR 0032.

## Context

The backend (dev) has:

- GET /students/me → `{ studentProfileId, profileImageUrl, name, university,
  studentNumber, introduction, portfolioUrl, proposalCount, completedJobCount,
  penaltyCount, averageRating, specialtyCategories [{ id, name, specialties
  [{ id, name }] }], certificates [{ certificateName, acquiredYear }],
  reviewCount, reviews, settlements }`. `studentNumber` is the two-digit
  admission year, there is no major, `reviews` are the latest three (store
  name, the job's specialties, rating, content, date; no job title), and
  `settlements` are the latest three settled rows. A non-student gets 403.
- PUT /students/me with `{ profileImageUrl, introduction, specialtyIds,
  certificates [{ certificateName, acquiredYear }], portfolioUrl }`. Every
  field is overwritten, and the specialties and certificates are replaced.
  URLs must be http(s) or empty. An unknown or repeated specialty is
  SPECIALTY_400 / SPECIALTY_400_DUPLICATE.
- GET /settlements → `{ summary { thisMonthWorkAmount, scheduledAmount,
  totalSettledAmount }, months [{ yearMonth, settlements [{ jobId, title,
  amount, settledDate, storeName, status }] }] }`, grouped by the Korean month
  of the payment. Status SCHEDULED (no date), SETTLED (the completion date),
  START_COMPENSATION (the owner cancelled during work; the student's
  compensation and the refund date), REFUNDED (the student declined before
  starting; 0원). Settled totals include compensation.

## Decision

- **Data** (`src/features/student/lib/studentMe.ts`, `useStudentMe` and
  `useStudentPhotoChange` in `src/features/student/hooks/useStudentMe.ts`,
  `src/features/student/lib/settlements.ts`, `useSettlementHistory`): 401 goes
  to /login; 403 shows an alert and goes to `landingPath()`.
- **내 정보** (`src/pages/StudentMePage.tsx`): name (`studentTitle`), 「광운대학교
  24학번」, the specialty categories (「디자인 / 홍보」), and the photo come from
  GET /students/me, and so does the 완료 count. 진행 중 counts the matched list
  (ADR 0032) instead of sample works. While loading or after a failure the
  profile part shows `LoadNotice` and the counts 「-」.
- **Photo** (내 정보 and 프로필 수정): choosing a photo checks its format and
  size as in signup, uploads it as PROFILE, and saves it with PUT
  /students/me with the current values; the new photo shows while uploading,
  and a failure shows an alert.
- **프로필 수정** (`src/pages/StudentProfilePage.tsx`): every part reads GET
  /students/me. Reviews show the store, the job's specialty names in place of
  the job title, the rating, the text when there is one, and the date;
  「받은 후기」 counts all reviews and 「전체 보기」 opens the three the server
  sends. The rating is 「-」 without reviews. Empty certificates and portfolio
  show 「아직 올린 자격증·포트폴리오가 없어요」. The portfolio shows without
  「https://」.
- **프로필 편집** (`src/pages/StudentProfileEditPage.tsx`): the form waits for
  GET /students/me and GET /specialties; the chips are the signup 3/3 chips
  with server ids. 「저장하기」 uploads a new photo first, sends the trimmed
  values (the portfolio gets 「https://」), shows 「저장하는 중...」, and goes back
  to 프로필 수정. A specialty error shows 「고른 특기를 다시 확인해 주세요」, another
  400 「입력한 내용을 다시 확인해 주세요」, and other failures 「잠시 후 다시 시도해
  주세요」, above the button.
- **정산 내역** (`src/pages/StudentSettlementsPage.tsx`): the summary, then
  the months in server order. Rows read 「가게 · 작업 중」 (정산 예정), 「가게 ·
  M월 D일 정산」 (정산 완료), 「가게 · M월 D일 사장님 사정 취소」 (착수 보상), and
  「가게 · M월 D일 성사되지 않음」 with 0원 (성사되지 않음). A 정산 예정 row opens
  내 활동 › 진행 중; the finished rows open 내 결과물 or 성사되지 않은 작업 (ADR
  0042). No rows → 「아직 정산 내역이 없어요」.
- **내 활동 › 완료 summary** (`src/pages/StudentActivityPage.tsx`): GET
  /settlements; the box hides while loading or after a failure. The 완료
  list reads the settlements too (ADR 0042).

## Rationale

- One GET /students/me fills 내 정보, 프로필 수정, and 프로필 편집, including
  the latest reviews and settlements.
- Showing every server settlement keeps each month's rows matching its
  summary, including the 0원 row of a declined request.

## Alternatives Considered

- Hiding the 0원 row of a declined request: rejected, the month would no
  longer match its summary.

## Agent Guidance

- When GET /students/me carries the major, show 「광운대학교 경영학부 24학번」
  as in Figma; when reviews carry the job title, show it in place of the
  specialty names.
- Show 「자동 완료 정산」 when the server says a job completed automatically.

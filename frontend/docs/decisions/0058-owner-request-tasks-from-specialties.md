# 0058. Owner 의뢰 등록 1/3 picks from GET /specialties

## Status

Accepted. 의뢰 등록 1/3 draws its fields and tasks from GET /specialties and
carries the picked specialty ids to POST /jobs. Replaces the name-based pick
and the send-time lookup in ADR 0028.

## Context

- 1/3 drew its cards and chips from the hardcoded `SPECIALTY_BADGES` and
  kept the picked work as names. 「의뢰 등록하기」 read GET /specialties and
  turned the names into ids (ADR 0028).
- 제안 보내기 2/4 already draws from GET /specialties (ADR 0020), so the two
  flows showed different lists when the server list differed from the
  hardcoded one.

## Decision

- **List**: `src/pages/OwnerRequestNewPage.tsx` reads `useSpecialties` and
  shows only categories with at least one specialty
  (`selectableCategories`). A category whose only specialty has its own name
  (「기타」) picks that specialty when its card is picked and shows no chips
  (`implicitSpecialty`); its card reads 「내용은 다음 단계에서 적어 주세요」.
- **Loading**: 「분야를 불러오는 중이에요」, a failure shows 「분야를 불러오지
  못했어요」 with 「다시 시도」 (`LoadNotice`), and an empty list shows 「고를
  수 있는 일이 아직 없어요」, the same as 2/4.
- **Icons and hints**: a category named like one of the six Figma fields
  uses that field's icon and gray hint; any other category uses the 「전체」
  icon and no hint.
- **State**: `NewRequestState` holds `categoryIds` and `picked`, each
  `PickedTask` being `{ specialtyId, name, categoryId, categoryName }`
  (`src/features/owner/lib/newRequest.ts`). 「다음」 needs one picked task.
  `toJobCreateRequest` sends the picked ids; 3/3 does not read
  GET /specialties. SPECIALTY_400 / SPECIALTY_400_DUPLICATE still show
  「할 일 목록이 바뀌었어요. 다시 골라 주세요」 and open 1/3 with the picks
  cleared.
- **Starting values**: `fitRequestChoice` keeps the carried categories and
  tasks that are in the loaded list, in their picked order, and adds the
  specialty of a picked 「기타」-like category.
  - A home example (`useRequestExample`) is matched by its field and task
    names (`exampleRequestChoice`, `findSpecialtyByName`); a name not on the
    server leaves nothing picked.
  - 「우리 가게에도 비슷한 의뢰 만들기」 uses `similarRequestState` with the
    detail's `specialtyCategories`: every category, and for a job also its
    specialties.
- 3/3 shows the picked category names as badges and the picked task names in
  「할 일」.
- `SPECIALTY_BADGES` is removed; `MAX_SPECIALTY_BADGES` stays in
  `src/types/specialty.ts`.

## Rationale

- One server list for the owner request and the student proposal keeps the
  same tasks and the same 「기타」 rule in both flows.
- Carrying ids from 1/3 removes the name lookup at send time and the failure
  when a name differs from the server.

## Alternatives Considered

- Keeping the names and looking up ids at send time (ADR 0028): the list on
  1/3 could differ from the server list.
- Sharing the 1/3 and 2/4 picker as one component: the two screens differ in
  tone, texts, and step state.

## Agent Guidance

- New entry points to 의뢰 등록 pass a `NewRequestState` with
  `categoryIds` and `picked`; 1/3 fits it to the loaded list.
- The 2/4 rules are in `src/pages/StudentProposalTasksPage.tsx`; keep both
  screens on `selectableCategories` and `implicitSpecialty`.

import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  AppImage,
  Button,
  Chip,
  FIELD_ICONS,
  LoadNotice,
  StepIndicator,
  SubScreen,
} from "../components";
import type { ImageName } from "../components";
import {
  OWNER_PATHS,
  exampleRequestChoice,
  fitRequestChoice,
  readNewRequestState,
  useRequestExample,
} from "../features/owner";
import type { NewRequestState, PickedTask, RequestChoice } from "../features/owner";
import { implicitSpecialty, selectableCategories, useSpecialties } from "../features/specialty";
import type { SpecialtyCategory } from "../features/specialty";
import { useBack } from "../hooks/useBack";
import { useDragScroll } from "../hooks/useDragScroll";
import { FIELDS } from "../types/field";
import type { Field } from "../types/field";
import "./OwnerRequestNewPage.css";

/** 분야 카드 아래 회색 설명 (분류 이름이 피그마 분야와 같을 때만) */
const FIELD_HINTS: Partial<Record<Field, string>> = {
  디자인: "메뉴판·전단지·로고",
  홍보: "사진·영상·SNS",
  "개발·IT": "홈페이지·예약·엑셀",
  분석: "리뷰·설문·매출",
  "글쓰기·번역": "소개 글·외국어",
  기타: "그 밖의 일",
};

function asField(name: string): Field | undefined {
  return FIELDS.find((field) => field === name);
}

/** 분류 이름이 피그마 분야와 같으면 그 3D 아이콘, 아니면 「전체」 아이콘 */
function categoryIcon(name: string): ImageName {
  const field = asField(name);
  return field ? FIELD_ICONS[field] : "iconFieldAll";
}

/**
 * 피그마 「의뢰 등록 1/3 - 기본 정보」. 분야를 고르고 분야마다 필요한 일을 고른다.
 * 분야와 일은 GET /specialties 에서 온다. 고를 일이 없는 분류는 보이지 않는다 (서버가 할 일을
 * 1개 이상 받는다). 「기타」처럼 일이 분류와 같은 이름으로 1개뿐이면 카드만 골라도 그 일이 골라진다
 * (학생 제안 2/4 와 같은 규칙, ADR 0058).
 * 홈 「이런 의뢰는 어때요?」 예시로 들어오면 같은 이름의 분야와 일이 골라져 있고, 2/3 에는 예시 내용이
 * 채워져 있다 (B-2). 「비슷한 의뢰 만들기」로 들어오면 그 의뢰서 · 제안서의 분야(와 일)가 골라져 있다.
 */
function OwnerRequestNewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(OWNER_PATHS.home);
  // 2/3 에서 ← 로 돌아오거나 「비슷한 의뢰 만들기」로 들어오면 들고 온 값으로 시작한다
  const saved = readNewRequestState(location.state);
  const exampleId = saved?.exampleId ?? (location.state as { exampleId?: string } | null)?.exampleId;
  const example = useRequestExample(exampleId);
  const { load, reload } = useSpecialties();
  // 손대기 전에는 null: 들고 온 값이나 예시를 목록이 오면 그때 보여준다
  const [choice, setChoice] = useState<RequestChoice | null>(null);

  const pickedScroll = useDragScroll<HTMLDivElement>();

  const categories: SpecialtyCategory[] =
    load.status === "loaded" ? selectableCategories(load.categories) : [];

  const startChoice = (): RequestChoice => {
    if (saved && saved.categoryIds.length > 0) return fitRequestChoice(categories, saved);
    return example ? exampleRequestChoice(categories, example) : { categoryIds: [], picked: [] };
  };
  const { categoryIds, picked } = choice ?? startChoice();

  const countOf = (categoryId: number) => picked.filter((p) => p.categoryId === categoryId).length;
  const isPicked = (specialtyId: number) => picked.some((p) => p.specialtyId === specialtyId);

  // 분야를 빼면 그 분야에서 고른 일도 함께 뺀다.
  // 「기타」처럼 카드로 정해지는 일은 카드를 고를 때 함께 고른다
  const toggleCategory = (category: SpecialtyCategory) => {
    if (categoryIds.includes(category.id)) {
      setChoice({
        categoryIds: categoryIds.filter((id) => id !== category.id),
        picked: picked.filter((p) => p.categoryId !== category.id),
      });
      return;
    }
    const only = implicitSpecialty(category);
    setChoice({
      categoryIds: [...categoryIds, category.id],
      picked:
        only && !isPicked(only.id)
          ? [
              ...picked,
              { specialtyId: only.id, name: only.name, categoryId: category.id, categoryName: category.name },
            ]
          : picked,
    });
  };

  // 할 일 칩을 보여줄 분류 (카드로 정해지는 분류는 뺀다)
  const taskGroups = categories.filter(
    (category) => categoryIds.includes(category.id) && !implicitSpecialty(category),
  );

  const toggleTask = (task: PickedTask) => {
    setChoice({
      categoryIds,
      picked: isPicked(task.specialtyId)
        ? picked.filter((p) => p.specialtyId !== task.specialtyId)
        : [...picked, task],
    });
  };

  const goNext = () => {
    // 할 일을 하나도 고르지 않은 분야는 빼고 넘긴다
    const next: NewRequestState = {
      ...saved,
      exampleId,
      categoryIds: categoryIds.filter((id) => countOf(id) > 0),
      picked,
    };
    navigate(location.pathname, { replace: true, state: next });
    navigate(OWNER_PATHS.newRequestStep(2), { state: next });
  };

  return (
    <SubScreen
      title="의뢰 등록"
      onBack={back}
      footer={
        <div className="owner-new__footer">
          {picked.length > 0 && (
            <div className="owner-new__picked" {...pickedScroll}>
              <strong>고른 일 {picked.length}</strong>
              {picked.map((task) => (
                <span key={task.specialtyId} className="owner-new__picked-item">
                  {task.name === task.categoryName ? task.name : `${task.categoryName} › ${task.name}`}
                  <button type="button" aria-label={`${task.name} 빼기`} onClick={() => toggleTask(task)}>
                    ✕
                  </button>
                </span>
              ))}
            </div>
          )}
          <Button fullWidth disabled={picked.length === 0} onClick={goNext}>
            다음
          </Button>
        </div>
      }
    >
      <div className="owner-new">
        <StepIndicator total={3} current={1} />

        <div className="owner-new__intro">
          <h2 className="owner-new__title">어떤 일을 맡기고 싶으세요?</h2>
          <p className="owner-new__description">
            여러 분야를 골라도 되고, 분야마다 필요한 일을 여러 개 골라도 돼요
          </p>
        </div>

        {load.status !== "loaded" ? (
          <LoadNotice
            status={load.status}
            loadingText="분야를 불러오는 중이에요"
            errorText="분야를 불러오지 못했어요"
            onRetry={reload}
          />
        ) : categories.length === 0 ? (
          <p className="owner-new__empty">고를 수 있는 일이 아직 없어요</p>
        ) : (
          <div className="owner-new__fields">
            {categories.map((category) => {
              const selected = categoryIds.includes(category.id);
              const count = countOf(category.id);
              const field = asField(category.name);
              const implicit = implicitSpecialty(category) !== undefined;
              const sub =
                selected && implicit
                  ? "내용은 다음 단계에서 적어 주세요"
                  : selected && count > 0
                    ? `${count}개 골랐어요`
                    : ((field && FIELD_HINTS[field]) ?? "");
              return (
                <button
                  key={category.id}
                  type="button"
                  className={`owner-new__field${selected ? " owner-new__field--selected" : ""}`}
                  aria-pressed={selected}
                  onClick={() => toggleCategory(category)}
                >
                  <AppImage name={categoryIcon(category.name)} width={36} height={36} alt="" />
                  {selected && (
                    <span className="owner-new__field-check" aria-hidden="true">
                      <svg viewBox="0 0 12 12" fill="none">
                        <path
                          d="M2.5 6.2 4.9 8.5 9.5 3.5"
                          stroke="currentColor"
                          strokeWidth="1.8"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </svg>
                    </span>
                  )}
                  <span className="owner-new__field-name">{category.name}</span>
                  <span className="owner-new__field-sub">{sub}</span>
                </button>
              );
            })}
          </div>
        )}

        {taskGroups.length > 0 && (
          <section className="owner-new__tasks">
            <div className="owner-new__tasks-head">
              <h2 className="owner-new__tasks-title">필요한 일을 골라 주세요</h2>
              <span>여러 개 골라도 돼요</span>
            </div>
            {taskGroups.map((category) => (
              <div key={category.id} className="owner-new__group">
                <div className="owner-new__group-head">
                  <AppImage name={categoryIcon(category.name)} width={22} height={22} alt="" />
                  <strong>{category.name}</strong>
                  <span>{countOf(category.id)}개</span>
                </div>
                <div className="owner-new__chips">
                  {category.specialties.map((specialty) => (
                    <Chip
                      key={specialty.id}
                      variant="outlined"
                      tone="owner"
                      label={specialty.name}
                      selected={isPicked(specialty.id)}
                      onClick={() =>
                        toggleTask({
                          specialtyId: specialty.id,
                          name: specialty.name,
                          categoryId: category.id,
                          categoryName: category.name,
                        })
                      }
                    />
                  ))}
                </div>
              </div>
            ))}
          </section>
        )}
      </div>
    </SubScreen>
  );
}

export default OwnerRequestNewPage;

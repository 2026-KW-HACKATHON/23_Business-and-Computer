import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { AppImage, Button, Chip, FIELD_ICONS, StepIndicator, SubScreen } from "../components";
import { STUDENT_PATHS, readNewProposalState, useProposalExample } from "../features/student";
import type { NewProposalState, PickedTask } from "../features/student";
import { useBack } from "../hooks/useBack";
import { FIELDS } from "../types/field";
import type { Field } from "../types/field";
import { SPECIALTY_BADGES } from "../types/specialty";
import "./StudentProposalNewPage.css";

/** 분야 카드 아래 회색 설명 */
const FIELD_HINTS: Record<Field, string> = {
  디자인: "메뉴판·전단지·로고",
  홍보: "사진·영상·SNS",
  "개발·IT": "홈페이지·예약·엑셀",
  분석: "리뷰·설문·매출",
  "글쓰기·번역": "소개 글·외국어",
  기타: "그 밖의 일",
};

/** 분야마다 고를 수 있는 일 (학생 역량 뱃지와 같은 목록) */
const TASKS_BY_FIELD = new Map(SPECIALTY_BADGES.map(({ field, badges }) => [field, badges]));

/**
 * 피그마 「제안 보내기 2/4 - 분야·할 일」. 분야를 고르고 분야마다 해 드릴 일을 고른다.
 * 홈 「이런 제안은 어때요?」 예시로 들어오면 그 예시의 분야와 일이 골라져 있다.
 */
function StudentProposalTasksPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(STUDENT_PATHS.newProposal);
  const saved = readNewProposalState(location.state);
  const example = useProposalExample(saved?.exampleId);
  const hasPicked = (saved?.fields.length ?? 0) > 0;
  const [fields, setFields] = useState<Field[]>(
    hasPicked ? (saved?.fields ?? []) : example ? [example.field] : [],
  );
  const [picked, setPicked] = useState<PickedTask[]>(
    hasPicked ? (saved?.picked ?? []) : example ? [{ field: example.field, task: example.task }] : [],
  );

  if (!saved?.storeId) return <Navigate to={STUDENT_PATHS.newProposal} replace />;

  const countOf = (field: Field) => picked.filter((p) => p.field === field).length;
  const isPicked = (field: Field, task: string) =>
    picked.some((p) => p.field === field && p.task === task);

  // 분야를 빼면 그 분야에서 고른 일도 함께 뺀다
  const toggleField = (field: Field) => {
    if (fields.includes(field)) {
      setFields(fields.filter((f) => f !== field));
      setPicked(picked.filter((p) => p.field !== field));
    } else {
      setFields([...fields, field]);
    }
  };

  const toggleTask = (field: Field, task: string) => {
    setPicked(
      isPicked(field, task)
        ? picked.filter((p) => !(p.field === field && p.task === task))
        : [...picked, { field, task }],
    );
  };

  // 기타는 고를 일이 없어 다음 단계에서 적는다
  const canNext = picked.length > 0 || fields.includes("기타");

  const goNext = () => {
    const next: NewProposalState = { ...saved, fields, picked };
    navigate(location.pathname, { replace: true, state: next });
    navigate(STUDENT_PATHS.newProposalStep(3), { state: next });
  };

  return (
    <SubScreen
      title="제안 보내기"
      onBack={back}
      footer={
        <div className="student-new__footer">
          {picked.length > 0 && (
            <div className="student-new__picked">
              <strong>고른 일 {picked.length}</strong>
              {picked.map(({ field, task }) => (
                <span key={`${field}-${task}`} className="student-new__picked-item">
                  {field} › {task}
                  <button
                    type="button"
                    aria-label={`${task} 빼기`}
                    onClick={() => toggleTask(field, task)}
                  >
                    ✕
                  </button>
                </span>
              ))}
            </div>
          )}
          <Button tone="student" fullWidth disabled={!canNext} onClick={goNext}>
            다음
          </Button>
        </div>
      }
    >
      <div className="student-new">
        <StepIndicator total={4} current={2} tone="student" />

        <div className="student-new__intro">
          <h2 className="student-new__title">어떤 일로 제안할까요?</h2>
          <p className="student-new__description">전공을 살려 가게에 해 드리고 싶은 일을 골라 주세요</p>
        </div>

        <div className="student-new__fields">
          {FIELDS.map((field) => {
            const selected = fields.includes(field);
            return (
              <button
                key={field}
                type="button"
                className={`student-new__field${selected ? " student-new__field--selected" : ""}`}
                aria-pressed={selected}
                onClick={() => toggleField(field)}
              >
                <AppImage name={FIELD_ICONS[field]} width={36} height={36} alt="" />
                {selected && (
                  <span className="student-new__field-check" aria-hidden="true">
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
                <span className="student-new__field-name">{field}</span>
                <span className="student-new__field-sub">
                  {selected && countOf(field) > 0 ? `${countOf(field)}개 골랐어요` : FIELD_HINTS[field]}
                </span>
              </button>
            );
          })}
        </div>

        {fields.length > 0 && (
          <section className="student-new__tasks">
            <div className="student-new__tasks-head">
              <h2 className="student-new__tasks-title">해 드릴 일을 골라 주세요</h2>
              <span>여러 개 골라도 돼요</span>
            </div>
            {FIELDS.filter((field) => fields.includes(field)).map((field) => {
              const tasks = TASKS_BY_FIELD.get(field) ?? [];
              return (
                <div key={field} className="student-new__group">
                  <div className="student-new__group-head">
                    <AppImage name={FIELD_ICONS[field]} width={22} height={22} alt="" />
                    <strong>{field}</strong>
                    {tasks.length > 0 && <span>{countOf(field)}개</span>}
                  </div>
                  {tasks.length > 0 ? (
                    <div className="student-new__chips">
                      {tasks.map((task) => (
                        <Chip
                          key={task}
                          variant="outlined"
                          tone="student"
                          label={task}
                          selected={isPicked(field, task)}
                          onClick={() => toggleTask(field, task)}
                        />
                      ))}
                    </div>
                  ) : (
                    <p className="student-new__group-note">해 드릴 일은 다음 단계에서 적어 주세요</p>
                  )}
                </div>
              );
            })}
          </section>
        )}
      </div>
    </SubScreen>
  );
}

export default StudentProposalTasksPage;

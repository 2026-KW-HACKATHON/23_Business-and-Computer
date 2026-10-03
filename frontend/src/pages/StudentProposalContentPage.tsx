import { useRef, useState } from "react";
import type { ChangeEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  BudgetField,
  Button,
  FormField,
  RevisionStepper,
  StepIndicator,
  SubScreen,
  TextAreaField,
  TextField,
  TitleField,
} from "../components";
import { STUDENT_PATHS, readNewProposalState, useProposalExample } from "../features/student";
import type { ProposalContent } from "../features/student";
import { useBack } from "../hooks/useBack";
import "./StudentProposalNewPage.css";

const MAX_PHOTOS = 5;

const EMPTY_CONTENT: ProposalContent = {
  title: "",
  problem: "",
  solution: "",
  plan: "",
  wishBudget: 0,
  draftDays: 0,
  finalDays: 0,
  revisions: 1,
  photos: [],
};

/** 1.8MB · 240KB */
function sizeText(bytes: number): string {
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)}MB`
    : `${Math.max(1, Math.round(bytes / 1024))}KB`;
}

/** 「초안까지 [2] 일」 같은 날 수 칸 */
function DaysField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (days: number) => void;
}) {
  return (
    <label className="student-new__days">
      <span>{label}</span>
      <TextField
        inputMode="numeric"
        value={value > 0 ? String(value) : ""}
        placeholder="0"
        trailing={<span className="student-new__days-unit">일</span>}
        onChange={(e) => onChange(Number(e.target.value.replace(/\D/g, "").slice(0, 2)) || 0)}
      />
    </label>
  );
}

/**
 * 피그마 「제안 보내기 3/4 - 내용 입력」. 제목 · 손님 눈으로 본 문제 · 이렇게 바꿔 드릴게요 ·
 * 작업계획서 · 희망 작업비 · 예상 기간 · 수정 횟수 · 참고 사진.
 */
function StudentProposalContentPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(STUDENT_PATHS.newProposal);
  const state = readNewProposalState(location.state);
  const example = useProposalExample(state?.exampleId);
  const [content, setContent] = useState<ProposalContent>(
    state?.content ?? { ...EMPTY_CONTENT, title: example?.proposalTitle ?? "" },
  );
  const fileInput = useRef<HTMLInputElement>(null);

  if (!state?.storeId || (state.fields.length === 0 && state.picked.length === 0)) {
    return <Navigate to={STUDENT_PATHS.newProposal} replace />;
  }

  const update = (patch: Partial<ProposalContent>) => setContent({ ...content, ...patch });

  const addPhotos = (e: ChangeEvent<HTMLInputElement>) => {
    const files = [...(e.target.files ?? [])].map((f) => ({ name: f.name, size: sizeText(f.size) }));
    update({ photos: [...content.photos, ...files].slice(0, MAX_PHOTOS) });
    e.target.value = "";
  };

  const canNext =
    content.title.trim() !== "" &&
    content.problem.trim() !== "" &&
    content.solution.trim() !== "" &&
    content.plan.trim() !== "" &&
    content.wishBudget > 0 &&
    content.draftDays > 0 &&
    content.finalDays >= content.draftDays;

  const goNext = () => {
    const next = {
      ...state,
      content: {
        ...content,
        title: content.title.trim(),
        problem: content.problem.trim(),
        solution: content.solution.trim(),
        plan: content.plan.trim(),
      },
    };
    navigate(location.pathname, { replace: true, state: next });
    navigate(STUDENT_PATHS.newProposalStep(4), { state: next });
  };

  return (
    <SubScreen
      title="제안 보내기"
      onBack={back}
      footer={
        <Button tone="student" fullWidth disabled={!canNext} onClick={goNext}>
          다음
        </Button>
      }
    >
      <div className="student-new">
        <StepIndicator total={4} current={3} tone="student" />

        <div className="student-new__intro">
          <h2 className="student-new__title">제안 내용을 알려 주세요</h2>
          <p className="student-new__description">사장님과 다른 학생들이 보고 공감할 수 있게 적어 주세요</p>
        </div>

        <FormField label="제안 제목" wrapsInput>
          <TitleField
            value={content.title}
            placeholder="예: 외국어 메뉴판 만들기"
            onChange={(title) => update({ title })}
          />
        </FormField>

        <FormField label="손님 눈으로 본 문제" hint="가게에서 불편했던 점을 손님 입장에서 적어 주세요" wrapsInput>
          <TextAreaField
            value={content.problem}
            maxLength={500}
            placeholder="예: 유학생 친구랑 오면 메뉴 설명이 어려워요"
            onChange={(problem) => update({ problem })}
          />
        </FormField>

        <FormField label="이렇게 바꿔 드릴게요" hint="전공을 살려 어떻게 바꿀지 적어 주세요" wrapsInput>
          <TextAreaField
            value={content.solution}
            maxLength={500}
            placeholder="예: 메뉴를 영어·중국어로 옮기고 사진을 넣은 메뉴판으로 만들어 드릴게요"
            onChange={(solution) => update({ solution })}
          />
        </FormField>

        <FormField label="작업계획서" hint="제안이 수락되면 이렇게 진행할게요" wrapsInput>
          <TextAreaField
            value={content.plan}
            maxLength={500}
            placeholder={"· 방법: \n· 일정: "}
            onChange={(plan) => update({ plan })}
          />
        </FormField>

        <FormField label="희망 작업비" hint="사장님이 수락할 때 최종 금액을 정해요" wrapsInput>
          <BudgetField value={content.wishBudget} onChange={(wishBudget) => update({ wishBudget })} />
        </FormField>

        <FormField label="예상 기간" hint="제안이 수락된 날부터 세요">
          <div className="student-new__days-row">
            <DaysField label="초안까지" value={content.draftDays} onChange={(draftDays) => update({ draftDays })} />
            <DaysField label="최종까지" value={content.finalDays} onChange={(finalDays) => update({ finalDays })} />
          </div>
        </FormField>

        <FormField label="수정 횟수" hint="최소 1회 · 등록한 뒤에는 바꿀 수 없어요">
          <RevisionStepper value={content.revisions} onChange={(revisions) => update({ revisions })} />
        </FormField>

        <FormField label="참고 사진" hint={`사진 · 최대 ${MAX_PHOTOS}장`}>
          <div className="student-new__photos">
            <input
              ref={fileInput}
              type="file"
              accept="image/*"
              multiple
              hidden
              onChange={addPhotos}
            />
            {content.photos.length < MAX_PHOTOS && (
              <button
                type="button"
                className="student-new__upload"
                onClick={() => fileInput.current?.click()}
              >
                + 사진 올리기
              </button>
            )}
            {content.photos.map((photo, i) => (
              <div key={`${photo.name}-${i}`} className="student-new__file">
                <span aria-hidden="true">📄</span>
                <strong>{photo.name}</strong>
                <small>{photo.size}</small>
                <button
                  type="button"
                  aria-label={`${photo.name} 빼기`}
                  onClick={() => update({ photos: content.photos.filter((_, j) => j !== i) })}
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        </FormField>
      </div>
    </SubScreen>
  );
}

export default StudentProposalContentPage;

import { useRef, useState } from "react";
import type { ChangeEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  BudgetField,
  Button,
  FormField,
  PhotoViewer,
  StepIndicator,
  SubScreen,
  TextAreaField,
  TextField,
  TitleField,
} from "../components";
import {
  MAX_PROPOSAL_PHOTOS,
  PROPOSAL_PHOTO_ACCEPT,
  STUDENT_PATHS,
  StoreConcernCard,
  checkProposalPhoto,
  readNewProposalState,
  useProposalExample,
} from "../features/student";
import type { ProposalContent } from "../features/student";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import "./StudentProposalNewPage.css";

const PHOTO_ERROR_TEXT = {
  type: "JPG, PNG, WEBP 사진만 올릴 수 있어요",
  size: "10MB 이하 사진만 올릴 수 있어요",
} as const;

const EMPTY_CONTENT: ProposalContent = {
  title: "",
  problem: "",
  solution: "",
  plan: "",
  wishBudget: 0,
  draftDays: 0,
  finalDays: 0,
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
 * 작업계획서 · 희망 작업비 · 예상 기간 · 참고 사진. 수정 횟수는 사장님이 의뢰서에서 정한다.
 * 고른 가게에 고민이 있으면 맨 위에 「사장님 고민」을 보여 참고하며 쓰게 한다 (ADR 0069).
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
  const photoUrls = useObjectUrls(content.photos);
  const [photoError, setPhotoError] = useState<keyof typeof PHOTO_ERROR_TEXT | null>(null);
  // 크게 보는 사진의 순서. 닫혀 있으면 null
  const [viewing, setViewing] = useState<number | null>(null);

  if (!state?.store || state.picked.length === 0) {
    return <Navigate to={STUDENT_PATHS.newProposal} replace />;
  }

  const update = (patch: Partial<ProposalContent>) => setContent({ ...content, ...patch });

  // 형식·크기가 맞지 않는 사진은 빼고, 빠진 이유 하나를 알린다 (백엔드 이미지 업로드와 같은 기준)
  const addPhotos = (e: ChangeEvent<HTMLInputElement>) => {
    const files = [...(e.target.files ?? [])];
    const rejected = files.map(checkProposalPhoto).find((check) => check !== "ok");
    const accepted = files.filter((file) => checkProposalPhoto(file) === "ok");
    setPhotoError(rejected ?? null);
    update({ photos: [...content.photos, ...accepted].slice(0, MAX_PROPOSAL_PHOTOS) });
    e.target.value = "";
  };

  // 최종 마감이 초안 마감 뒤로 며칠인지 (두 번째 칸에 보이는 값)
  const finalGap = Math.max(0, content.finalDays - content.draftDays);

  const canNext =
    content.title.trim() !== "" &&
    content.problem.trim() !== "" &&
    content.solution.trim() !== "" &&
    content.plan.trim() !== "" &&
    content.wishBudget > 0 &&
    content.draftDays > 0 &&
    content.finalDays > content.draftDays;

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

        {state.store.concern && <StoreConcernCard storeName={state.store.name} concern={state.store.concern} />}

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

        <FormField label="예상 기간" hint="초안은 수락된 날부터, 최종은 초안 마감부터 세요">
          {/* 서버는 두 값 모두 수락된 날부터 센다. 두 번째 칸은 초안 뒤 며칠이라, 보낼 때 최종 = 초안 + 그 값 */}
          <div className="student-new__days-row">
            <DaysField
              label="초안까지"
              value={content.draftDays}
              onChange={(draftDays) => update({ draftDays, finalDays: draftDays + finalGap })}
            />
            <DaysField
              label="초안 뒤 최종까지"
              value={finalGap}
              onChange={(gap) => update({ finalDays: content.draftDays + gap })}
            />
          </div>
        </FormField>

        <FormField label="참고 사진" hint={`JPG·PNG·WEBP 사진 · 최대 ${MAX_PROPOSAL_PHOTOS}장 · 장당 10MB`}>
          <div className="student-new__photos">
            <input
              ref={fileInput}
              type="file"
              accept={PROPOSAL_PHOTO_ACCEPT}
              multiple
              hidden
              onChange={addPhotos}
            />
            {content.photos.length < MAX_PROPOSAL_PHOTOS && (
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
                <button
                  type="button"
                  className="student-new__thumb-button"
                  aria-label={`${photo.name} 크게 보기`}
                  onClick={() => setViewing(i)}
                >
                  <img className="student-new__thumb" src={photoUrls[i]} alt="" />
                </button>
                <strong>{photo.name}</strong>
                <small>{sizeText(photo.size)}</small>
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
          {viewing !== null && viewing < content.photos.length && (
            <PhotoViewer
              photos={content.photos.map((photo, i) => ({ url: photoUrls[i], name: photo.name }))}
              index={viewing}
              onIndex={setViewing}
              onClose={() => setViewing(null)}
            />
          )}
          {photoError && (
            <p className="student-new__photo-error" role="alert">
              {PHOTO_ERROR_TEXT[photoError]}
            </p>
          )}
        </FormField>
      </div>
    </SubScreen>
  );
}

export default StudentProposalContentPage;

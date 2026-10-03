import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  BudgetField,
  Button,
  DueDateFields,
  FormField,
  RevisionStepper,
  StepIndicator,
  SubScreen,
  TextAreaField,
  TitleField,
} from "../components";
import {
  OWNER_PATHS,
  dueDatesReady,
  readNewRequestState,
  useRequestExample,
} from "../features/owner";
import type { RequestContent } from "../features/owner";
import { useBack } from "../hooks/useBack";
import "./OwnerRequestNewPage.css";

const EMPTY_CONTENT: RequestContent = {
  title: "",
  description: "",
  budget: 0,
  draftDue: "",
  finalDue: "",
  revisions: 1,
};

/**
 * 피그마 「의뢰 등록 2/3 - 내용 입력」. 제목 · 맡기고 싶은 일 · 작업비 · 마감일 · 수정 횟수.
 * 홈 예시 카드로 들어왔으면 그 예시 내용이 채워져 있다 (B-2).
 */
function OwnerRequestContentPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(OWNER_PATHS.newRequest);
  const state = readNewRequestState(location.state);
  const example = useRequestExample(state?.exampleId);
  const [content, setContent] = useState<RequestContent>(
    state?.content ?? example?.content ?? EMPTY_CONTENT,
  );

  if (!state) return <Navigate to={OWNER_PATHS.newRequest} replace />;

  const update = (patch: Partial<RequestContent>) => setContent({ ...content, ...patch });

  const canNext =
    content.title.trim() !== "" &&
    content.description.trim() !== "" &&
    content.budget > 0 &&
    dueDatesReady(content);

  const goNext = () => {
    const next = {
      ...state,
      content: { ...content, title: content.title.trim(), description: content.description.trim() },
    };
    navigate(location.pathname, { replace: true, state: next });
    navigate(OWNER_PATHS.newRequestStep(3), { state: next });
  };

  return (
    <SubScreen
      title="의뢰 등록"
      onBack={back}
      footer={
        <Button fullWidth disabled={!canNext} onClick={goNext}>
          다음
        </Button>
      }
    >
      <div className="owner-new">
        <StepIndicator total={3} current={2} />

        <div className="owner-new__intro">
          <h2 className="owner-new__title">의뢰 내용을 알려 주세요</h2>
          <p className="owner-new__description">학생들이 보고 지원할 수 있게 짧게 적어 주세요</p>
        </div>

        <FormField label="의뢰 제목" wrapsInput>
          <TitleField
            value={content.title}
            placeholder="예: 영어·중국어 메뉴판 번역"
            onChange={(title) => update({ title })}
          />
        </FormField>

        <FormField
          label="맡기고 싶은 일"
          hint="무엇을, 왜, 어떻게 바꾸고 싶은지 편하게 적어 주세요"
          wrapsInput
        >
          <TextAreaField
            value={content.description}
            maxLength={500}
            placeholder="예: 외국인 손님이 늘어서 메뉴판을 영어랑 중국어로 바꾸고 싶어요"
            onChange={(description) => update({ description })}
          />
        </FormField>

        <FormField label="작업비" hint="학생에게 줄 금액을 직접 적어 주세요" wrapsInput>
          <BudgetField value={content.budget} onChange={(budget) => update({ budget })} />
        </FormField>

        <FormField label="마감일" hint="초안 마감 → 수정 → 최종 마감 순서로 진행돼요">
          <DueDateFields value={content} onChange={(dues) => update(dues)} />
        </FormField>

        <FormField label="수정 횟수" hint="최소 1회 · 등록한 뒤에는 바꿀 수 없어요">
          <RevisionStepper
            value={content.revisions}
            onChange={(revisions) => update({ revisions })}
          />
        </FormField>
      </div>
    </SubScreen>
  );
}

export default OwnerRequestContentPage;

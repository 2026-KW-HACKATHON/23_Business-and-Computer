import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  AppImage,
  AttachmentTiles,
  Button,
  CategoryBadge,
  InfoRows,
  StepIndicator,
  SubScreen,
  TextButton,
} from "../components";
import {
  STUDENT_PATHS,
  expectedDaysText,
  proposalTaskSummary,
  readNewProposalState,
  sendProposal,
  useStore,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { todayIsoDate } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentProposalNewPage.css";

/** 피그마 「제안 보내기 4/4 - 확인」. 3/4 에서 적은 내용을 제안서 모양으로 보여 준다 */
function StudentProposalConfirmPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(STUDENT_PATHS.newProposal);
  const state = readNewProposalState(location.state);
  const store = useStore(state?.storeId);
  // 두 번 눌러 같은 제안이 두 번 가지 않게 한 번 누르면 잠근다
  const [sending, setSending] = useState(false);

  if (!state?.content || !store) return <Navigate to={STUDENT_PATHS.newProposal} replace />;
  const { content } = state;

  const send = () => {
    if (sending) return;
    setSending(true);
    sendProposal({
      id: `prop-new-${Date.now()}`,
      title: content.title,
      field: state.fields[0] ?? state.picked[0]?.field ?? "기타",
      tasks: state.picked.map((p) => p.task),
      store: { id: store.id, name: store.name },
      sentOn: todayIsoDate(),
      empathyCount: 0,
      status: "waiting",
      seenByOwner: false,
      problem: content.problem,
      solution: content.solution,
      plan: content.plan,
      wishBudget: content.wishBudget,
      draftDays: content.draftDays,
      finalDays: content.finalDays,
      attachments: content.photos.map((p) => p.name),
    });
    navigate(STUDENT_PATHS.newProposalDone, { replace: true });
  };

  return (
    <SubScreen
      title="제안 보내기"
      onBack={back}
      footer={
        <Button tone="student" fullWidth disabled={sending} onClick={send}>
          제안 보내기
        </Button>
      }
    >
      <div className="student-new">
        <StepIndicator total={4} current={4} tone="student" />

        <div className="student-new__intro">
          <h2 className="student-new__title">제안서를 확인해 주세요</h2>
          <p className="student-new__description">
            보내면 사장님께 알림이 가고, 다른 광운대생에게도 공개돼요
          </p>
        </div>

        <article className="student-confirm__card">
          <div className="student-confirm__head">
            <AppImage name="iconCardProposal" width={24} height={24} />
            <h3 className="student-confirm__title">{content.title}</h3>
          </div>
          <div className="student-confirm__badges">
            {state.fields.map((field) => (
              <CategoryBadge key={field} field={field} />
            ))}
          </div>
          <InfoRows
            rows={[
              { label: "할 일", value: proposalTaskSummary(state) },
              { label: "받는 가게", value: store.name },
              { label: "희망 작업비", value: formatWon(content.wishBudget) },
              { label: "예상 기간", value: expectedDaysText(content.draftDays, content.finalDays) },
            ]}
          />
          <hr className="student-confirm__divider" />
          <div className="student-confirm__text">
            <p className="student-confirm__text-title">손님 눈으로 본 문제</p>
            <p className="student-confirm__text-body">{content.problem}</p>
          </div>
          <div className="student-confirm__text">
            <p className="student-confirm__text-title">이렇게 바꿔 드릴게요</p>
            <p className="student-confirm__text-body">{content.solution}</p>
          </div>
          <div className="student-confirm__text">
            <p className="student-confirm__text-title">작업계획서</p>
            <p className="student-confirm__text-body">{content.plan}</p>
          </div>
          {content.photos.length > 0 && (
            <AttachmentTiles names={content.photos.map((p) => p.name)} height={90} />
          )}
          <hr className="student-confirm__divider" />
          <TextButton className="student-confirm__edit" onClick={back}>
            내용 고치기
          </TextButton>
        </article>

        <div className="student-confirm__notice">
          <strong>사장님이 수락하면 의뢰서가 와요</strong>
          <p>
            사장님이 작업비·마감일·수정 횟수를 정해 의뢰서를 보내요. 내가 확인하고 동의하면 작업이
            시작돼요.
          </p>
        </div>
      </div>
    </SubScreen>
  );
}

export default StudentProposalConfirmPage;

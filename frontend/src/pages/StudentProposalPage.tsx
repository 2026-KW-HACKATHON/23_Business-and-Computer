import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  AttachmentTiles,
  Button,
  CategoryBadge,
  Dialog,
  FlowBar,
  InfoRows,
  SubScreen,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import {
  STUDENT_PATHS,
  StoreBox,
  StudentMissing,
  cancelMyProposal,
  expectedDaysText,
  flowSteps,
  useMyProposal,
  useStore,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";

/**
 * 피그마 「보낸 제안서 상세 보기」. 수락을 기다리는 제안은 「제안 취소」(확인 → 완료 팝업),
 * 수락돼 의뢰서가 온 제안은 「조건 확인하기」로 작업 시작 화면에 간다.
 */
function StudentProposalPage() {
  const { proposalId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.activity("proposals"));
  const proposal = useMyProposal(proposalId);
  const store = useStore(proposal?.store.id);
  const [cancelStep, setCancelStep] = useState<"closed" | "confirm" | "done">("closed");

  if (!proposal && cancelStep !== "done") {
    return <StudentMissing title="보낸 제안" onBack={back} />;
  }

  const confirmCancel = () => {
    cancelMyProposal(proposalId);
    setCancelStep("done");
  };

  const accepted = proposal?.status === "accepted";

  return (
    <SubScreen
      title="보낸 제안"
      onBack={back}
      footer={
        proposal &&
        (accepted && proposal.workId ? (
          <Button
            tone="student"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.workStart(proposal.workId ?? ""))}
          >
            조건 확인하기
          </Button>
        ) : (
          <div className="student-detail__actions">
            <Button variant="secondary" onClick={() => setCancelStep("confirm")}>
              제안 취소
            </Button>
            <Button tone="student" onClick={back}>
              확인
            </Button>
          </div>
        ))
      }
    >
      {proposal && (
        <div className="student-detail student-proposal">
          <div className="student-detail__heading">
            <div className="student-detail__title-row">
              <WorkKindIcon kind="proposal" size={28} />
              <h2 className="student-detail__title">{proposal.title}</h2>
            </div>
            <div className="student-detail__meta">
              <CategoryBadge field={proposal.field} />
              {formatMonthDay(proposal.sentOn)} 보냄
            </div>
          </div>

          <FlowBar
            tone="student"
            steps={accepted ? flowSteps("제안", 1, "동의해 주세요") : flowSteps("제안", 0, "수락 대기")}
          />

          <div className="student-proposal__empathy">
            <AppImage name="iconHeart" width={24} alt="" />
            <div>
              <strong className="student-proposal__empathy-title">
                학생 손님 {proposal.empathyCount}명이 공감했어요
              </strong>
              <p className="student-proposal__empathy-sub">
                공감이 많이 모이면 사장님께 한 번 더 알려 드려요
              </p>
            </div>
          </div>

          <StoreBox
            name={proposal.store.name}
            address={store?.address}
            note={
              accepted
                ? "사장님이 제안을 받아들였어요"
                : proposal.seenByOwner
                  ? "사장님이 제안을 확인했어요"
                  : "사장님이 아직 확인하지 않았어요"
            }
          />

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">손님 눈으로 본 문제</h2>
            <p className="student-detail__text">{proposal.problem}</p>
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">이렇게 바꿔 드릴게요</h2>
            <p className="student-detail__text">{proposal.solution}</p>
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">작업계획서</h2>
            <WorkPlan plan={proposal.plan} />
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">희망 작업비 · 예상 기간</h2>
            <div className="student-detail__box">
              <InfoRows
                size="large"
                rows={[
                  { label: "희망 작업비", value: formatWon(proposal.wishBudget) },
                  { label: "예상 기간", value: expectedDaysText(proposal.draftDays, proposal.finalDays) },
                ]}
              />
            </div>
          </section>

          {proposal.attachments.length > 0 && (
            <section className="student-detail__section">
              <h2 className="student-detail__section-title">참고 사진</h2>
              <AttachmentTiles names={proposal.attachments} />
            </section>
          )}

          <p className="student-detail__footnote">
            사장님이 수락하면 이 제안을 바탕으로 작업비·마감일·수정 횟수를 정한 의뢰서가 와요.
          </p>
        </div>
      )}

      <Dialog
        open={cancelStep === "confirm"}
        image="warningStudent"
        title="제안을 취소할까요?"
        description="사장님께 보낸 제안이 사라지고, 모인 공감도 함께 없어져요."
        onClose={() => setCancelStep("closed")}
        actions={
          <>
            <Button tone="student" fullWidth onClick={confirmCancel}>
              제안 취소하기
            </Button>
            <Button variant="secondary" fullWidth onClick={() => setCancelStep("closed")}>
              돌아가기
            </Button>
          </>
        }
      />
      <Dialog
        open={cancelStep === "done"}
        image="doneStudent"
        title="제안을 취소했어요"
        actions={
          <Button
            tone="student"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.activity("proposals"), { replace: true })}
          >
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default StudentProposalPage;

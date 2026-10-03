import { useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  AttachmentTiles,
  Button,
  Checkbox,
  Dialog,
  FlowBar,
  InfoRows,
  NoteBox,
  NumberedSteps,
  SubScreen,
  TurnNotice,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import {
  STUDENT_PATHS,
  StoreBox,
  StudentMissing,
  agreeToWork,
  declineWork,
  expectedDaysText,
  useMyProposal,
  useStore,
  useStudentWork,
  workFlowSteps,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/**
 * 피그마 「작업 시작 - 의뢰서 확인·약관 동의」. 내 제안이 수락돼 사장님 의뢰서가 오면
 * 조건을 확인하고 약관에 동의해 작업을 시작한다. 조건이 어려우면 거절할 수 있다.
 */
function StudentWorkStartPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const work = useStudentWork(workId);
  const proposal = useMyProposal(work?.proposalId);
  const store = useStore(work?.store.id);
  const [agreed, setAgreed] = useState(false);
  const [popup, setPopup] = useState<"none" | "started" | "decline" | "declined">("none");
  // 동의하면 제안이 보낸 제안 목록에서 빠지므로, 팝업 뒤 화면에는 처음 본 제안서를 그대로 둔다
  const [firstProposal] = useState(proposal);
  const shownProposal = proposal ?? firstProposal;

  // 거절하면 작업이 목록에서 빠진다. 이동하는 동안은 빈 화면
  if (!work) return popup === "declined" ? null : <StudentMissing title="작업 시작" onBack={back} />;
  // 이미 시작한 작업은 지금 할 일 화면으로
  if (work.status !== "awaitingAgreement" && popup !== "started") {
    return <Navigate to={STUDENT_PATHS.workSubmit(work.id)} replace />;
  }

  const start = () => {
    agreeToWork(work.id);
    setPopup("started");
  };

  const decline = () => {
    setPopup("declined");
    declineWork(work.id);
    navigate(STUDENT_PATHS.activity("proposals"), { replace: true });
  };

  return (
    <SubScreen
      title="작업 시작"
      onBack={back}
      footer={
        <div className="student-detail__actions">
          <Button variant="secondary" onClick={() => setPopup("decline")}>
            이 조건은 어려워요
          </Button>
          <Button tone="student" disabled={!agreed} onClick={start}>
            동의하고 작업 시작하기
          </Button>
        </div>
      }
    >
      <div className="student-detail">
        <FlowBar tone="student" steps={workFlowSteps(work, "동의해 주세요")} />

        <TurnNotice
          tone="student"
          title="의뢰서 조건을 확인하고 동의해 주세요"
          description="동의하면 바로 작업이 시작돼요"
        />

        <StoreBox
          name={work.store.name}
          address={store?.address}
          note={work.requestArrivedOn ? `의뢰서 ${formatMonthDay(work.requestArrivedOn)} 도착` : undefined}
        />

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">사장님이 보낸 의뢰서</h2>
          <div className="student-work__request">
            <div className="student-work__request-head">
              <WorkKindIcon kind="request" size={22} />
              <strong>{work.title}</strong>
            </div>
            <InfoRows
              size="large"
              rows={[
                { label: "작업비", value: formatWon(work.budget) },
                { label: "초안 마감", value: formatMonthDayWeekday(work.draftDue) },
                { label: "최종 마감", value: formatMonthDayWeekday(work.finalDue) },
                { label: "수정", value: `${work.revisionLimit}회` },
              ]}
            />
          </div>
        </section>

        {work.ownerMessage && (
          <NoteBox title={`${work.store.name} 사장님의 한마디`} body={work.ownerMessage} />
        )}

        {shownProposal && (
          <section className="student-detail__section">
            <h2 className="student-detail__section-title">내가 보낸 제안서</h2>
            <div className="student-work__proposal">
              <p className="student-work__label">손님 눈으로 본 문제</p>
              <p className="student-detail__text">{shownProposal.problem}</p>
              <p className="student-work__label">이렇게 바꿔 드릴게요</p>
              <p className="student-detail__text">{shownProposal.solution}</p>
              <p className="student-work__label">작업계획서</p>
              <WorkPlan text={shownProposal.plan} />
              <p className="student-work__label">희망 작업비 · 예상 기간</p>
              <p className="student-detail__text">
                {formatWon(shownProposal.wishBudget)} · {expectedDaysText(shownProposal.draftDays, shownProposal.finalDays)}
              </p>
              {shownProposal.attachments.length > 0 && (
                <AttachmentTiles names={shownProposal.attachments} height={90} />
              )}
            </div>
          </section>
        )}

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">시작 전에 약속해요</h2>
          <NumberedSteps
            variant="card"
            steps={[
              { title: "마감일까지 결과물을 낼게요", description: "작업비는 가꿈이 보관하고, 완료되면 정산돼요" },
              { title: `수정 요청은 정한 횟수(${work.revisionLimit}회)만큼 반영할게요` },
              { title: "연락 없이 마감을 넘기면 노쇼로 기록돼요" },
            ]}
          />
        </section>

        <Checkbox
          checked={agreed}
          onChange={setAgreed}
          label="책임 약관과 취소·환불 기준에 동의해요 (필수)"
        />
      </div>

      <Dialog
        open={popup === "started"}
        image="doneStudent"
        title="작업을 시작했어요"
        description={`초안은 ${formatMonthDay(work.draftDue)}까지 제출해 주세요.\n채팅방이 열렸어요. 사장님께도 알릴게요.`}
        actions={
          <Button
            tone="student"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.activity("inProgress"), { replace: true })}
          >
            확인
          </Button>
        }
      />
      <Dialog
        open={popup === "decline"}
        image="warningStudent"
        title="의뢰서를 거절할까요?"
        description={"사장님께 거절 알림이 가고, 맡겨 둔 작업비는 사장님께 모두 돌아가요."}
        onClose={() => setPopup("none")}
        actions={
          <>
            <Button tone="student" fullWidth onClick={decline}>
              거절하기
            </Button>
            <Button variant="secondary" fullWidth onClick={() => setPopup("none")}>
              돌아가기
            </Button>
          </>
        }
      />
    </SubScreen>
  );
}

export default StudentWorkStartPage;

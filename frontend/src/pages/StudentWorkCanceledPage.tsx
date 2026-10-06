import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { Button, Dialog, SubScreen } from "../components";
import { STUDENT_PATHS, StudentMissing, WorkSummary, useStudentWork } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/**
 * 피그마 「성사되지 않은 작업 상세 (학생)」. 취소 이유 · 사장님이 남긴 말 · 정산 받은 금액.
 * 알림 「사장님이 작업을 취소했어요」로 들어오면 먼저 「의뢰 취소 알림 - 사장님 사정」 팝업.
 */
function StudentWorkCanceledPage() {
  const { workId = "" } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.activity("done"));
  const work = useStudentWork(workId);
  const fromNotice = (location.state as { notice?: boolean } | null)?.notice === true;
  const [noticeOpen, setNoticeOpen] = useState(fromNotice);

  const closeNotice = () => {
    setNoticeOpen(false);
    navigate(location.pathname, { replace: true, state: null });
  };

  if (!work?.cancel) return <StudentMissing title="성사되지 않은 작업" onBack={back} />;
  const { cancel } = work;
  const canceledOn = formatMonthDay(cancel.canceledOn);
  const refund = work.budget - cancel.reward;

  return (
    <SubScreen
      title="성사되지 않은 작업"
      onBack={back}
      footer={
        <Button tone="student" fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      <div className="student-detail">
        <WorkSummary
          kind={work.kind}
          title={work.title}
          meta={`${work.store.name}, 사장님이 취소, 작업비 ${formatWon(work.budget)}`}
        />

        <div className="student-work__info">
          <strong>{canceledOn}에 성사되지 않은 작업이에요</strong>
          <p>
            {cancel.reward > 0
              ? "사장님 사정으로 취소돼 착수 보상 20%가 정산됐어요."
              : "작업을 시작하기 전에 취소돼 정산된 금액이 없어요."}
          </p>
        </div>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">취소 이유</h2>
          <p className="student-detail__text">{cancel.reason}</p>
        </section>

        {cancel.message && (
          <section className="student-detail__section">
            <h2 className="student-detail__section-title">사장님이 남긴 말</h2>
            <p className="student-detail__text">{cancel.message}</p>
          </section>
        )}

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">정산 받은 금액</h2>
          <div className="student-work__breakdown">
            <p>
              <span>작업비 (안전결제)</span>
              <span>{formatWon(work.budget)}</span>
            </p>
            <p>
              <span>사장님에게 환불</span>
              <span>- {formatWon(refund)}</span>
            </p>
            <hr />
            <p className="student-work__breakdown-total">
              <span>착수 보상</span>
              <strong>{formatWon(cancel.reward)}</strong>
            </p>
          </div>
          {cancel.reward > 0 && (
            <p className="student-detail__footnote">{canceledOn}에 착수 보상으로 정산됐어요.</p>
          )}
        </section>
      </div>

      <Dialog
        open={noticeOpen}
        image="warningStudent"
        title="사장님이 작업을 취소했어요"
        description={
          cancel.reward > 0
            ? "사장님 사정으로 취소돼 착수 보상이 정산돼요."
            : "작업을 시작하기 전에 취소됐어요."
        }
        onClose={closeNotice}
        actions={
          <Button tone="student" fullWidth onClick={closeNotice}>
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default StudentWorkCanceledPage;

import { useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { Button, Dialog, LoadNotice, SubScreen } from "../components";
import { useJobDetail } from "../features/explore";
import {
  STUDENT_PATHS,
  StudentMissing,
  WorkSummary,
  useProposalJobIds,
  useStudentWork,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, koreaDateOfUtc } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/** 성사되지 않은 작업 한 장에 보이는 것 */
interface CanceledView {
  kind: "proposal" | "request";
  title: string;
  /** 제목 아래 회색 줄 */
  meta: string;
  /** 「M월 D일」. 모르면 비움 */
  canceledOn?: string;
  description: string;
  reason?: string;
  message?: string;
  /** 결제한 작업만: 작업비 · 사장님에게 환불 · 착수 보상 */
  money?: { budget: number; refund: number; reward: number };
  /** 「사장님이 작업을 취소했어요」 팝업 설명 */
  noticeText: string;
}

/**
 * 피그마 「성사되지 않은 작업 상세 (학생)」. 취소 이유 · 사장님이 남긴 말 · 정산 받은 금액.
 * 알림 「사장님이 작업을 취소했어요」로 들어오면 먼저 「의뢰 취소 알림 - 사장님 사정」 팝업.
 * 주소의 id 가 숫자면 서버 작업(GET /jobs/{id} 의 취소 정보, ADR 0042), 아니면 샘플 작업.
 */
function StudentWorkCanceledPage() {
  const { workId = "" } = useParams();
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <JobCanceled workId={workId} jobId={jobId} />
  ) : (
    <SampleCanceled workId={workId} />
  );
}

/**
 * 서버 작업. 취소 정보는 맡은 학생에게만 온다. 내가 의뢰서를 거절했는지, 사장님이 작업 중에
 * 취소했는지(착수 보상), 시작 전에 취소했는지마다 설명이 다르다.
 */
function JobCanceled({ workId, jobId }: { workId: string; jobId: number }) {
  const back = useBack(STUDENT_PATHS.activity("done"));
  const { load, reload } = useJobDetail(workId);
  const proposalJobIds = useProposalJobIds();

  if (load.status === "notFound") return <StudentMissing title="성사되지 않은 작업" onBack={back} />;
  if (load.status !== "loaded") {
    return (
      <SubScreen title="성사되지 않은 작업" onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="작업을 불러오는 중이에요"
          errorText="작업을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  const { job } = load;
  if (job.status !== "CANCELLED" || !job.cancelledBy) {
    return <StudentMissing title="성사되지 않은 작업" onBack={back} />;
  }

  const declined = job.cancelledBy === "STUDENT";
  const reward = job.studentCompensationAmount ?? 0;
  const paid = job.refundAmount !== undefined && job.refundAmount !== null;
  const store = job.storeName?.trim() || "가게";
  const description = declined
    ? "작업을 시작하기 전에 의뢰서를 거절해 정산된 금액이 없어요. 작업비는 사장님께 모두 돌아갔어요."
    : reward > 0
      ? "사장님 사정으로 취소돼 착수 보상 20%가 정산됐어요."
      : "작업을 시작하기 전에 취소돼 정산된 금액이 없어요.";

  return (
    <CanceledScreen
      onBack={back}
      view={{
        kind: proposalJobIds.has(jobId) ? "proposal" : "request",
        title: job.title,
        meta: `${store}, ${declined ? "의뢰서 거절" : "사장님이 취소"}, 작업비 ${formatWon(job.budget)}`,
        // 취소 시각은 UTC (오프셋 없음)라 한국 날짜로 바꾼다
        canceledOn: job.cancelledAt ? formatMonthDay(koreaDateOfUtc(job.cancelledAt)) : undefined,
        description,
        reason: declined ? undefined : job.cancelReason?.trim() || undefined,
        message: job.messageToStudent?.trim() || undefined,
        money: paid ? { budget: job.budget, refund: job.refundAmount ?? 0, reward } : undefined,
        noticeText:
          reward > 0 ? "사장님 사정으로 취소돼 착수 보상이 정산돼요." : "작업을 시작하기 전에 취소됐어요.",
      }}
    />
  );
}

/** 샘플 작업 (알림 · 채팅의 예시) */
function SampleCanceled({ workId }: { workId: string }) {
  const back = useBack(STUDENT_PATHS.activity("done"));
  const work = useStudentWork(workId);

  if (!work?.cancel) return <StudentMissing title="성사되지 않은 작업" onBack={back} />;
  const { cancel } = work;
  return (
    <CanceledScreen
      onBack={back}
      view={{
        kind: work.kind,
        title: work.title,
        meta: `${work.store.name}, 사장님이 취소, 작업비 ${formatWon(work.budget)}`,
        canceledOn: formatMonthDay(cancel.canceledOn),
        description:
          cancel.reward > 0
            ? "사장님 사정으로 취소돼 착수 보상 20%가 정산됐어요."
            : "작업을 시작하기 전에 취소돼 정산된 금액이 없어요.",
        reason: cancel.reason,
        message: cancel.message,
        money: { budget: work.budget, refund: work.budget - cancel.reward, reward: cancel.reward },
        noticeText:
          cancel.reward > 0 ? "사장님 사정으로 취소돼 착수 보상이 정산돼요." : "작업을 시작하기 전에 취소됐어요.",
      }}
    />
  );
}

function CanceledScreen({ view, onBack }: { view: CanceledView; onBack: () => void }) {
  const location = useLocation();
  const navigate = useNavigate();
  const fromNotice = (location.state as { notice?: boolean } | null)?.notice === true;
  const [noticeOpen, setNoticeOpen] = useState(fromNotice);

  const closeNotice = () => {
    setNoticeOpen(false);
    navigate(location.pathname, { replace: true, state: null });
  };

  return (
    <SubScreen
      title="성사되지 않은 작업"
      onBack={onBack}
      footer={
        <Button tone="student" fullWidth onClick={onBack}>
          확인
        </Button>
      }
    >
      <div className="student-detail">
        <WorkSummary kind={view.kind} title={view.title} meta={view.meta} />

        <div className="student-work__info">
          <strong>
            {view.canceledOn ? `${view.canceledOn}에 성사되지 않은 작업이에요` : "성사되지 않은 작업이에요"}
          </strong>
          <p>{view.description}</p>
        </div>

        {view.reason && (
          <section className="student-detail__section">
            <h2 className="student-detail__section-title">취소 이유</h2>
            <p className="student-detail__text">{view.reason}</p>
          </section>
        )}

        {view.message && (
          <section className="student-detail__section">
            <h2 className="student-detail__section-title">사장님이 남긴 말</h2>
            <p className="student-detail__text">{view.message}</p>
          </section>
        )}

        {view.money && (
          <section className="student-detail__section">
            <h2 className="student-detail__section-title">정산 받은 금액</h2>
            <div className="student-work__breakdown">
              <p>
                <span>작업비 (안전결제)</span>
                <span>{formatWon(view.money.budget)}</span>
              </p>
              <p>
                <span>사장님에게 환불</span>
                <span>- {formatWon(view.money.refund)}</span>
              </p>
              <hr />
              <p className="student-work__breakdown-total">
                <span>착수 보상</span>
                <strong>{formatWon(view.money.reward)}</strong>
              </p>
            </div>
            {view.money.reward > 0 && view.canceledOn && (
              <p className="student-detail__footnote">{view.canceledOn}에 착수 보상으로 정산됐어요.</p>
            )}
          </section>
        )}
      </div>

      <Dialog
        open={noticeOpen}
        image="warningStudent"
        title="사장님이 작업을 취소했어요"
        description={view.noticeText}
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

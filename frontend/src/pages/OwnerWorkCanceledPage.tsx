import { useParams } from "react-router-dom";
import { Button, LoadNotice, SubScreen, WorkKindIcon } from "../components";
import { useJobDetail } from "../features/explore";
import {
  OWNER_PATHS,
  OwnerMissing,
  RefundBreakdown,
  parsePositiveId,
  useOwnerClosedJobs,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, koreaDate } from "../lib/date";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";
import "./OwnerWorkCancelPage.css";

/**
 * 피그마 「성사되지 않은 작업 상세 (사장님)」. 취소 이유 · 남긴 말 · 돌려받은 금액.
 * 서버 작업(GET /jobs/{id} 의 취소 정보, ADR 0036). 주소의 id 가 숫자가 아니면 찾을 수 없음.
 */
function OwnerWorkCanceledPage() {
  const { workId = "" } = useParams();
  const back = useBack(OWNER_PATHS.activity("done"));
  return parsePositiveId(workId) !== undefined ? (
    <JobCanceled workId={workId} />
  ) : (
    <OwnerMissing title="성사되지 않은 작업" onBack={back} />
  );
}

/**
 * 서버 작업. 취소 정보 · 작업비는 GET /jobs/{id}, 학생 이름 · 종류는 끝난 작업 목록에서 본다.
 * 학생의 의뢰서 거절 · 작업 중 취소 · 모집 중 취소마다 설명이 다르고, 결제한 작업만 돌려받은 금액을 보인다.
 */
function JobCanceled({ workId }: { workId: string }) {
  const back = useBack(OWNER_PATHS.activity("done"));
  const { load, reload } = useJobDetail(workId);
  const { load: closedLoad } = useOwnerClosedJobs();

  if (load.status === "notFound") return <OwnerMissing title="성사되지 않은 작업" onBack={back} />;
  if (load.status !== "loaded") {
    return (
      <SubScreen title="성사되지 않은 작업" onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="작업을 불러오는 중이에요"
          errorText="작업을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  const { job } = load;
  if (job.status !== "CANCELLED") return <OwnerMissing title="성사되지 않은 작업" onBack={back} />;

  const closed = closedLoad.status === "loaded" ? closedLoad.jobs.find((j) => j.jobId === job.id) : undefined;
  // 취소 시각의 한국 날짜
  const canceledDate = job.cancelledAt ? koreaDate(job.cancelledAt) : closed?.closedOn;
  const canceledOn = canceledDate ? formatMonthDay(canceledDate) : "";
  const declined = job.cancelledBy === "STUDENT";
  // 맡은 학생이 있었던(결제한) 의뢰만 환불 정보가 온다
  const paid = job.refundAmount !== undefined && job.refundAmount !== null;
  const reward = job.studentCompensationAmount ?? 0;
  const stage = declined
    ? "학생이 의뢰서 거절"
    : !paid
      ? "모집 중 취소"
      : reward > 0
        ? "작업 중 취소"
        : "작업 시작 전 취소";
  const description = declined
    ? "학생이 작업을 시작하기 전에 의뢰서를 거절해서 작업비를 모두 돌려받았어요."
    : !paid
      ? "학생을 고르기 전에 취소해서 결제한 금액이 없어요."
      : reward > 0
        ? "학생이 이미 작업을 시작해서 착수 보상 20%를 뺀 금액을 돌려받았어요."
        : "작업을 시작하기 전에 취소해서 작업비를 모두 돌려받았어요.";
  const meta = [
    closed?.studentName && studentTitle(closed.studentName),
    stage,
    `작업비 ${formatWon(job.budget)}`,
  ]
    .filter(Boolean)
    .join(", ");

  return (
    <SubScreen
      title="성사되지 않은 작업"
      onBack={back}
      footer={
        <Button fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      <div className="owner-cancel">
        <section className="owner-cancel__work">
          <div className="owner-cancel__work-head">
            <WorkKindIcon kind={closed?.kind ?? "request"} size={22} />
            <h2 className="owner-cancel__work-title">{job.title}</h2>
          </div>
          <p className="owner-cancel__meta">{meta}</p>
        </section>

        <div className="owner-cancel__intro">
          <h2 className="owner-cancel__title">
            {canceledOn ? `${canceledOn}에 성사되지 않은 작업이에요` : "성사되지 않은 작업이에요"}
          </h2>
          <p className="owner-cancel__description">{description}</p>
        </div>

        {!declined && job.cancelReason && (
          <div className="owner-cancel__readonly">
            <span className="owner-cancel__readonly-label">취소 이유</span>
            <p className="owner-cancel__readonly-text">{job.cancelReason}</p>
          </div>
        )}

        {job.messageToStudent && (
          <div className="owner-cancel__readonly">
            <span className="owner-cancel__readonly-label">학생에게 남긴 말</span>
            <p className="owner-cancel__readonly-text">{job.messageToStudent}</p>
          </div>
        )}

        {paid && (
          <RefundBreakdown
            title="돌려받은 금액"
            amount={job.budget}
            reward={reward}
            note={canceledOn ? `${canceledOn}에 결제한 수단으로 돌려받았어요.` : "결제한 수단으로 돌려받았어요."}
          />
        )}
      </div>
    </SubScreen>
  );
}

export default OwnerWorkCanceledPage;

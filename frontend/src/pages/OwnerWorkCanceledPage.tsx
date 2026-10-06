import { useParams } from "react-router-dom";
import { Button, SubScreen, WorkKindIcon } from "../components";
import { OWNER_PATHS, OwnerMissing, RefundBreakdown, useOwnerWork } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerWorkCancelPage.css";

/** 피그마 「성사되지 않은 작업 상세 (사장님)」. 취소 이유 · 남긴 말 · 돌려받은 금액 */
function OwnerWorkCanceledPage() {
  const { workId = "" } = useParams();
  const back = useBack(OWNER_PATHS.activity("done"));
  const work = useOwnerWork(workId);

  if (!work?.cancel) return <OwnerMissing title="성사되지 않은 작업" onBack={back} />;
  const { cancel } = work;
  const started = cancel.stage === "inProgress";
  const canceledOn = formatMonthDay(cancel.canceledOn);

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
            <WorkKindIcon kind={work.kind} size={22} />
            <h2 className="owner-cancel__work-title">{work.title}</h2>
          </div>
          <p className="owner-cancel__meta">
            {work.student.name} 학생 · {started ? "작업 중 취소" : "작업 시작 전 취소"} · 작업비{" "}
            {formatWon(work.budget)}
          </p>
        </section>

        <div className="owner-cancel__intro">
          <h2 className="owner-cancel__title">{canceledOn}에 성사되지 않은 작업이에요</h2>
          <p className="owner-cancel__description">
            {started
              ? "학생이 이미 작업을 시작해서 착수 보상 20%를 뺀 금액을 돌려받았어요."
              : "작업을 시작하기 전에 취소해서 작업비를 모두 돌려받았어요."}
          </p>
        </div>

        <div className="owner-cancel__readonly">
          <span className="owner-cancel__readonly-label">취소 이유</span>
          <p className="owner-cancel__readonly-text">{cancel.reason}</p>
        </div>

        {cancel.message && (
          <div className="owner-cancel__readonly">
            <span className="owner-cancel__readonly-label">학생에게 남긴 말</span>
            <p className="owner-cancel__readonly-text">{cancel.message}</p>
          </div>
        )}

        <RefundBreakdown
          title="돌려받은 금액"
          amount={work.budget}
          reward={work.budget - cancel.refund}
          note={`${canceledOn}에 결제한 수단으로 돌려받았어요.`}
        />
      </div>
    </SubScreen>
  );
}

export default OwnerWorkCanceledPage;

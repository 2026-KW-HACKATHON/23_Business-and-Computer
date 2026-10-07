import { useNavigate } from "react-router-dom";
import { LoadNotice, SubScreen } from "../components";
import {
  SETTLEMENT_STATUS_LABEL,
  STUDENT_PATHS,
  SettlementSummaryBox,
  settlementDetailText,
  settlementSummaryOf,
  useSettlementHistory,
} from "../features/student";
import type { SettlementItem, SettlementStatus } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./StudentSettlementsPage.css";

/** 상태 칩 색 (정산 예정 · 정산 완료 · 착수 보상 · 성사되지 않음) */
const STATUS_CLASS: Record<SettlementStatus, string> = {
  SCHEDULED: "expected",
  SETTLED: "settled",
  START_COMPENSATION: "reward",
  REFUNDED: "refunded",
};

/**
 * 피그마 「정산 내역 (학생)」. GET /settlements (ADR 0041) 의 요약과 결제한 달마다 묶은 정산을
 * 서버 순서대로 보여 주고, 정산 예정 · 정산 완료 · 착수 보상 · 성사되지 않음을 칩으로 보여 준다.
 * 작업 중인 줄은 내 활동 › 진행 중을 연다. 끝난 줄은 내 결과물 · 성사되지 않은 작업이 서버를 읽을 때 연결한다.
 */
function StudentSettlementsPage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.me);
  const { load, reload } = useSettlementHistory();

  if (load.status !== "loaded") {
    return (
      <SubScreen title="정산 내역" onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="정산 내역을 불러오는 중이에요"
          errorText="정산 내역을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  const months = load.data.months.filter((month) => month.settlements.length > 0);

  const rowContent = (s: SettlementItem) => (
    <>
      <span className="student-settlements__info">
        <span className="student-settlements__title">{s.title}</span>
        <span className="student-settlements__detail">{settlementDetailText(s)}</span>
      </span>
      <span className="student-settlements__side">
        <span className="student-settlements__amount">{formatWon(s.amount)}</span>
        <span className={`student-settlements__status student-settlements__status--${STATUS_CLASS[s.status]}`}>
          {SETTLEMENT_STATUS_LABEL[s.status]}
        </span>
      </span>
    </>
  );

  return (
    <SubScreen title="정산 내역" onBack={back}>
      <div className="student-settlements">
        <SettlementSummaryBox summary={settlementSummaryOf(load.data)} />
        <p className="student-settlements__note">
          사장님이 완료를 확인하면 작업비가 정산돼요. 해커톤 기간에는 수수료가 없어요.
        </p>

        {months.length === 0 && <p className="student-settlements__empty">아직 정산 내역이 없어요</p>}

        {months.map((month) => {
          const [year, mm] = month.yearMonth.split("-");
          return (
            <section key={month.yearMonth} className="student-settlements__month">
              <h2 className="student-settlements__month-title">
                {year}년 {Number(mm)}월
              </h2>
              <ul className="student-settlements__list">
                {month.settlements.map((s) => (
                  <li key={s.jobId}>
                    {s.status === "SCHEDULED" ? (
                      <button
                        type="button"
                        className="student-settlements__row"
                        onClick={() => navigate(STUDENT_PATHS.activity("inProgress"))}
                      >
                        {rowContent(s)}
                      </button>
                    ) : (
                      <div className="student-settlements__row student-settlements__row--static">
                        {rowContent(s)}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          );
        })}
      </div>
    </SubScreen>
  );
}

export default StudentSettlementsPage;

import { useNavigate } from "react-router-dom";
import { LoadNotice, SubScreen } from "../components";
import {
  OWNER_PATHS,
  PAYMENT_STATUS_LABEL,
  PaymentSummaryBox,
  paymentDetailText,
  paymentSummaryOf,
  useOwnerPaymentHistory,
} from "../features/owner";
import type { OwnerPaymentItem, PaymentHistoryStatus } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./OwnerPaymentsPage.css";

/** 상태 칩 색 (보관 중 · 정산 완료 · 환불) */
const STATUS_CLASS: Record<PaymentHistoryStatus, string> = {
  HELD: "escrowed",
  SETTLED: "settled",
  PARTIALLY_REFUNDED: "partialRefund",
  FULLY_REFUNDED: "fullRefund",
};

/**
 * 피그마 「결제 내역」. GET /payments (ADR 0040) 의 요약과 달마다 묶은 결제를 서버 순서대로 보여 주고,
 * 보관 중 · 정산 완료 · 환불을 칩으로 보여 준다.
 */
function OwnerPaymentsPage() {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.me);
  const { load, reload } = useOwnerPaymentHistory();

  // 보관 중 → 내 활동(진행 중), 정산 완료 → 결과물, 환불 → 성사되지 않은 작업
  const open = (p: OwnerPaymentItem) => {
    const id = String(p.jobId);
    if (p.status === "HELD") navigate(OWNER_PATHS.activity("inProgress"));
    else if (p.status === "SETTLED") navigate(OWNER_PATHS.workResult(id));
    else navigate(OWNER_PATHS.workCanceled(id));
  };

  if (load.status !== "loaded") {
    return (
      <SubScreen title="결제 내역" onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="결제 내역을 불러오는 중이에요"
          errorText="결제 내역을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  const months = load.data.months.filter((month) => month.payments.length > 0);

  return (
    <SubScreen title="결제 내역" onBack={back}>
      <div className="owner-payments">
        <PaymentSummaryBox summary={paymentSummaryOf(load.data)} />
        <p className="owner-payments__note">
          안전결제한 작업비는 완료를 확인할 때까지 골목인턴이 보관해요. 해커톤 기간에는 수수료가 없어요.
        </p>

        {months.length === 0 && <p className="owner-payments__empty">아직 결제한 의뢰가 없어요</p>}

        {months.map((month) => {
          const [year, mm] = month.yearMonth.split("-");
          return (
            <section key={month.yearMonth} className="owner-payments__month">
              <h2 className="owner-payments__month-title">
                {year}년 {Number(mm)}월
              </h2>
              <ul className="owner-payments__list">
                {month.payments.map((p) => (
                  <li key={p.jobId}>
                    <button type="button" className="owner-payments__row" onClick={() => open(p)}>
                      <span className="owner-payments__info">
                        <span className="owner-payments__title">{p.title}</span>
                        <span className="owner-payments__detail">{paymentDetailText(p)}</span>
                      </span>
                      <span className="owner-payments__side">
                        <span className="owner-payments__amount">{formatWon(p.amount)}</span>
                        <span className={`owner-payments__status owner-payments__status--${STATUS_CLASS[p.status]}`}>
                          {PAYMENT_STATUS_LABEL[p.status]}
                        </span>
                      </span>
                    </button>
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

export default OwnerPaymentsPage;

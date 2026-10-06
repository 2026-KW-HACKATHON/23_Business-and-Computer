import { useNavigate } from "react-router-dom";
import { SubScreen } from "../components";
import { OWNER_PATHS, PaymentSummaryBox, useOwnerPayments } from "../features/owner";
import type { OwnerPayment } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerPaymentsPage.css";

const STATUS_LABEL: Record<OwnerPayment["status"], string> = {
  escrowed: "보관 중",
  settled: "정산 완료",
  partialRefund: "부분 환불",
  fullRefund: "전액 환불",
};

/** 정산 · 환불이 있으면 그날, 아니면 결제한 날 */
const eventDate = (p: OwnerPayment) => p.refund?.on ?? p.settledOn ?? p.paidOn;

/** 「이은서 학생 · 8월 10일 작업 중 취소 · 64,000원 환불」 */
function detailOf(p: OwnerPayment): string {
  const student = `${p.studentName} 학생`;
  if (p.refund) {
    return `${student} · ${formatMonthDay(p.refund.on)} 작업 중 취소 · ${formatWon(p.refund.amount)} 환불`;
  }
  if (p.status === "settled" && p.settledOn) {
    return `${student} · ${formatMonthDay(p.settledOn)} ${p.autoCompleted ? "자동 완료 " : ""}정산`;
  }
  return `${student} · ${formatMonthDay(p.paidOn)}`;
}

/** 피그마 「결제 내역」. 달마다 묶고, 보관 중 · 정산 완료 · 환불을 칩으로 보여 준다 */
function OwnerPaymentsPage() {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.me);
  const { payments, summary } = useOwnerPayments();

  // 「2026년 9월」처럼 최근 달부터
  const sorted = [...payments].sort((a, b) => eventDate(b).localeCompare(eventDate(a)));
  const months = [...new Set(sorted.map((p) => eventDate(p).slice(0, 7)))];

  // 보관 중 → 내 활동(진행 중), 정산 완료 → 결과물, 환불 → 성사되지 않은 작업
  const open = (p: OwnerPayment) => {
    if (p.status === "escrowed") navigate(OWNER_PATHS.activity("inProgress"));
    else if (p.status === "settled") navigate(OWNER_PATHS.workResult(p.workId));
    else navigate(OWNER_PATHS.workCanceled(p.workId));
  };

  return (
    <SubScreen title="결제 내역" onBack={back}>
      <div className="owner-payments">
        <PaymentSummaryBox summary={summary} />
        <p className="owner-payments__note">
          안전결제한 작업비는 완료를 확인할 때까지 골목인턴이 보관해요. 해커톤 기간에는 수수료가 없어요.
        </p>

        {months.map((month) => {
          const [year, mm] = month.split("-");
          return (
            <section key={month} className="owner-payments__month">
              <h2 className="owner-payments__month-title">
                {year}년 {Number(mm)}월
              </h2>
              <ul className="owner-payments__list">
                {sorted
                  .filter((p) => eventDate(p).startsWith(month))
                  .map((p) => (
                    <li key={p.id}>
                      <button type="button" className="owner-payments__row" onClick={() => open(p)}>
                        <span className="owner-payments__info">
                          <span className="owner-payments__title">{p.title}</span>
                          <span className="owner-payments__detail">{detailOf(p)}</span>
                        </span>
                        <span className="owner-payments__side">
                          <span className="owner-payments__amount">{formatWon(p.amount)}</span>
                          <span className={`owner-payments__status owner-payments__status--${p.status}`}>
                            {STATUS_LABEL[p.status]}
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

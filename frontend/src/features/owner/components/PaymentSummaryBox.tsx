import { formatWon } from "../../../lib/money";
import type { PaymentSummary } from "../types";
import "./PaymentSummaryBox.css";

interface PaymentSummaryBoxProps {
  summary: PaymentSummary;
}

/** 결제 요약 3칸 (이번 달 결제 · 가꿈이 보관 중 · 정산 완료) */
function PaymentSummaryBox({ summary }: PaymentSummaryBoxProps) {
  const items = [
    { label: "이번 달 결제", amount: summary.thisMonth },
    { label: "가꿈이 보관 중", amount: summary.escrowed },
    { label: "정산 완료", amount: summary.settled },
  ];

  return (
    <dl className="payment-summary">
      {items.map(({ label, amount }) => (
        <div key={label} className="payment-summary__item">
          <dt>{label}</dt>
          <dd>{formatWon(amount)}</dd>
        </div>
      ))}
    </dl>
  );
}

export default PaymentSummaryBox;

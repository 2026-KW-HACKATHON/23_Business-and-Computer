import { formatWon } from "../../../lib/money";
import type { SettlementSummary } from "../types";
import "./SettlementSummaryBox.css";

interface SettlementSummaryBoxProps {
  summary: SettlementSummary;
}

/** 정산 요약 3칸 (이번 달 작업비 · 정산 예정 · 정산 완료) */
function SettlementSummaryBox({ summary }: SettlementSummaryBoxProps) {
  const items = [
    { label: "이번 달 작업비", amount: summary.thisMonth },
    { label: "정산 예정", amount: summary.expected },
    { label: "정산 완료", amount: summary.settled },
  ];

  return (
    <dl className="settlement-summary">
      {items.map(({ label, amount }) => (
        <div key={label} className="settlement-summary__item">
          <dt>{label}</dt>
          <dd>{formatWon(amount)}</dd>
        </div>
      ))}
    </dl>
  );
}

export default SettlementSummaryBox;

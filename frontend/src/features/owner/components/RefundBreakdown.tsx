import { formatWon } from "../../../lib/money";
import "./RefundBreakdown.css";

interface RefundBreakdownProps {
  /** 「돌려받는 금액」 또는 「돌려받은 금액」 */
  title: string;
  /** 안전결제한 작업비 */
  amount: number;
  /** 학생 착수 보상. 작업 시작 전 취소면 0 */
  reward: number;
  note: string;
}

/** 작업 취소 환불 계산 (작업비 − 착수 보상 = 돌려받는 금액) */
function RefundBreakdown({ title, amount, reward, note }: RefundBreakdownProps) {
  return (
    <div className="refund-breakdown">
      <strong className="refund-breakdown__title">{title}</strong>
      <dl className="refund-breakdown__rows">
        <div className="refund-breakdown__row">
          <dt>작업비 (안전결제)</dt>
          <dd>{formatWon(amount)}</dd>
        </div>
        {reward > 0 && (
          <div className="refund-breakdown__row">
            <dt>학생 착수 보상 (20%)</dt>
            <dd>- {formatWon(reward)}</dd>
          </div>
        )}
        <div className="refund-breakdown__row refund-breakdown__row--total">
          <dt>{title}</dt>
          <dd>{formatWon(amount - reward)}</dd>
        </div>
      </dl>
      <p className="refund-breakdown__note">{note}</p>
    </div>
  );
}

export default RefundBreakdown;

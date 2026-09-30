import { Fragment } from "react";
import "./SummaryCard.css";

export interface SummaryItem {
  label: string;
  count: number;
  /** 기본 「건」 */
  unit?: string;
}

interface SummaryCardProps {
  /** 사장님: 보낸 의뢰 · 받은 제안 · 진행 중 · 완료 / 학생: 지원한 의뢰 · 보낸 제안 · 진행 중 · 완료 */
  items: SummaryItem[];
  /** 넣으면 칸을 누를 수 있다 */
  onSelect?: (index: number) => void;
  className?: string;
}

/** 홈 위쪽 요약 카드 (글래스). 스크롤할 때 위에 고정해서 쓴다 */
function SummaryCard({ items, onSelect, className = "" }: SummaryCardProps) {
  return (
    <div className={`summary-card ${className}`.trim()}>
      {items.map(({ label, count, unit = "건" }, i) => {
        const content = (
          <>
            <span className="summary-card__label">{label}</span>
            <span className="summary-card__value">
              <strong className="summary-card__count">{count}</strong>
              <span className="summary-card__unit">{unit}</span>
            </span>
          </>
        );
        return (
          <Fragment key={label}>
            {i > 0 && <span className="summary-card__divider" aria-hidden="true" />}
            {onSelect ? (
              <button type="button" className="summary-card__item" onClick={() => onSelect(i)}>
                {content}
              </button>
            ) : (
              <div className="summary-card__item">{content}</div>
            )}
          </Fragment>
        );
      })}
    </div>
  );
}

export default SummaryCard;

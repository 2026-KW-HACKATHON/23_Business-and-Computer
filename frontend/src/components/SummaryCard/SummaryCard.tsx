import { Fragment } from "react";
import LoadingDots from "../LoadingDots/LoadingDots";
import "./SummaryCard.css";

export interface SummaryItem {
  label: string;
  /** 아직 불러오는 중이면 null: 숫자 자리에 점 세 개 */
  count: number | string | null;
  /** 기본 「건」 */
  unit?: string;
}

interface SummaryCardProps {
  /** 사장님: 보낸 의뢰 · 받은 제안 · 진행 중 · 완료 / 학생: 지원한 의뢰 · 보낸 제안 · 진행 중 · 완료 */
  items: SummaryItem[];
  /** 넣으면 칸을 누를 수 있다 */
  onSelect?: (index: number) => void;
  /** 넣으면 탭처럼 쓴다. 고른 칸은 회색 알약 + 굵은 글자 (내 활동) */
  selectedIndex?: number;
  className?: string;
}

/** 홈 위쪽 요약 카드 (글래스). 스크롤할 때 위에 고정해서 쓴다 */
function SummaryCard({ items, onSelect, selectedIndex, className = "" }: SummaryCardProps) {
  const tabs = selectedIndex !== undefined;
  return (
    <div className={`summary-card${tabs ? " summary-card--tabs" : ""} ${className}`.trim()}>
      {items.map(({ label, count, unit = "건" }, i) => {
        const selected = i === selectedIndex;
        const itemClass = `summary-card__item${selected ? " summary-card__item--selected" : ""}`;
        // 탭 모양에서는 고른 칸 양옆 구분선을 지운다
        const showDivider = i > 0 && !(tabs && (selected || i - 1 === selectedIndex));
        const content = (
          <>
            <span className="summary-card__label">{label}</span>
            <span className="summary-card__value">
              {count === null ? (
                <LoadingDots className="summary-card__loading" label={`${label} 불러오는 중`} />
              ) : (
                <>
                  <strong className="summary-card__count">{count}</strong>
                  <span className="summary-card__unit">{unit}</span>
                </>
              )}
            </span>
          </>
        );
        return (
          <Fragment key={label}>
            {showDivider && <span className="summary-card__divider" aria-hidden="true" />}
            {onSelect ? (
              <button
                type="button"
                className={itemClass}
                aria-pressed={tabs ? selected : undefined}
                onClick={() => onSelect(i)}
              >
                {content}
              </button>
            ) : (
              <div className={itemClass}>{content}</div>
            )}
          </Fragment>
        );
      })}
    </div>
  );
}

export default SummaryCard;

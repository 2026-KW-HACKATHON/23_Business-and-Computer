import "./SectionHeader.css";

interface SectionHeaderProps {
  title: string;
  /** 제목 옆 회색 숫자 */
  count?: number;
  /** 오른쪽 글자 버튼 (예: 더 보기 ›, 펼치기 ›, 접기) */
  actionLabel?: string;
  onAction?: () => void;
  /** 오른쪽 버튼이 목록을 펼치고 접을 때만 넣는다 */
  expanded?: boolean;
}

/** 홈 섹션 제목. 「확인할 일 3」처럼 제목 + 개수, 오른쪽에 글자 버튼 */
function SectionHeader({ title, count, actionLabel, onAction, expanded }: SectionHeaderProps) {
  return (
    <div className="section-header">
      <h2 className="section-header__title">
        {title}
        {count !== undefined && <span className="section-header__count">{count}</span>}
      </h2>
      {actionLabel && onAction && (
        <button
          type="button"
          className="section-header__action"
          aria-expanded={expanded}
          onClick={onAction}
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
}

export default SectionHeader;

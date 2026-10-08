import { useDelayedShow } from "../../hooks/useDelayedShow";
import "./SkeletonList.css";

interface SkeletonListProps {
  /** rows = 아이콘 + 두 줄 목록, cards = 버튼이 있는 카드, block = 칸 하나 */
  kind?: "rows" | "cards" | "block";
  /** 회색 틀 개수. 기본 rows · cards 3, block 1 */
  count?: number;
  /** 화면 낭독기가 읽는 글 (예: 알림을 불러오는 중이에요) */
  label: string;
}

/** 목록 · 카드를 처음 불러오는 동안 그 모양대로 보이는 회색 틀. 0.3초 안에 끝나면 그리지 않는다 */
function SkeletonList({ kind = "rows", count = kind === "block" ? 1 : 3, label }: SkeletonListProps) {
  const shown = useDelayedShow();
  return (
    <div className={`skeleton-list skeleton-list--${kind}`} role="status" aria-live="polite">
      <span className="skeleton-list__sr">{label}</span>
      {shown &&
        Array.from({ length: count }, (_, i) => (
          <div key={i} className="skeleton-list__item" aria-hidden="true">
            {kind === "rows" && <span className="skeleton skeleton--icon" />}
            <span className="skeleton-list__lines">
              <span className="skeleton skeleton--line" />
              <span className="skeleton skeleton--line skeleton--short" />
              {kind === "cards" && <span className="skeleton skeleton--button" />}
            </span>
          </div>
        ))}
    </div>
  );
}

export default SkeletonList;

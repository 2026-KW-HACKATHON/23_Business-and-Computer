import { CategoryBadge } from "../../../components";
import type { StoreConcern } from "../types";
import "./StoreConcern.css";

interface StoreConcernCardProps {
  storeName: string;
  concern: StoreConcern;
}

/** 제안 내용을 쓰는 화면 위 「사장님 고민」. 한 줄 · 분야 · 자세한 설명을 보며 제안을 쓴다 */
function StoreConcernCard({ storeName, concern }: StoreConcernCardProps) {
  return (
    <section className="store-concern-card" aria-label={`${storeName} 사장님 고민`}>
      <div className="store-concern-card__head">
        <span className="store-concern-card__label">사장님 고민</span>
        {concern.category && <CategoryBadge field={concern.category} />}
      </div>
      <p className="store-concern-card__title">{concern.title}</p>
      {concern.description && <p className="store-concern-card__body">{concern.description}</p>}
    </section>
  );
}

export default StoreConcernCard;

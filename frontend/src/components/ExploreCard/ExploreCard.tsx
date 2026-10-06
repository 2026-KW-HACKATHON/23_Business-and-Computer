import type { WorkKind } from "../../types/workKind";
import CategoryBadge from "../CategoryBadge/CategoryBadge";
import DeadlineBadge from "../DeadlineBadge/DeadlineBadge";
import EmpathyCount from "../EmpathyCount/EmpathyCount";
import TextButton from "../TextButton/TextButton";
import WorkKindIcon from "../WorkKindIcon/WorkKindIcon";
import "./ExploreCard.css";

interface ExploreCardProps {
  kind: WorkKind;
  title: string;
  /** 대분류 이름 (겹치지 않게). 하나씩 뱃지로 */
  fields: string[];
  storeName: string;
  /** 제안만: 공감 수 */
  empathyCount?: number;
  /** 의뢰만: 모집 중일 때 마감 (예: 9월 27일까지) */
  deadline?: { stage: "draft" | "final"; due: string };
  /** 굵은 진행 상태 (예: 수락 대기, 완료) */
  status?: string;
  /** 「상세 보기」 오른쪽 작은 안내 (예: 우리 가게가 받은 제안이에요) */
  hint?: string;
  onOpen: () => void;
}

/** 탐색 목록 카드 (다른 가게의 제안·의뢰) */
function ExploreCard({
  kind,
  title,
  fields,
  storeName,
  empathyCount,
  deadline,
  status,
  hint,
  onOpen,
}: ExploreCardProps) {
  return (
    <article className="explore-card">
      <div className="explore-card__head">
        <WorkKindIcon kind={kind} size={22} />
        <h3 className="explore-card__title">{title}</h3>
        {empathyCount !== undefined && <EmpathyCount count={empathyCount} empathized />}
        {deadline && <DeadlineBadge stage={deadline.stage} due={deadline.due} />}
      </div>
      <div className="explore-card__meta">
        {fields.map((name) => (
          <CategoryBadge key={name} field={name} />
        ))}
        <span className="explore-card__store">{storeName}</span>
      </div>
      {status && <p className="explore-card__status">{status}</p>}
      <div className="explore-card__divider" />
      <div className="explore-card__footer">
        <TextButton onClick={onOpen}>
          {kind === "proposal" ? "제안서 상세 보기" : "의뢰서 상세 보기"}
        </TextButton>
        {hint && <span className="explore-card__hint">{hint}</span>}
      </div>
    </article>
  );
}

export default ExploreCard;

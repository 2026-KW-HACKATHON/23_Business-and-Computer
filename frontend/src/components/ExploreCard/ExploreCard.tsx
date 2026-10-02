import type { Field } from "../../types/field";
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
  field: Field;
  storeName: string;
  /** 제안만: 공감 수 */
  empathyCount?: number;
  /** 의뢰만: 모집 중일 때 마감 (예: 9월 27일까지) */
  deadline?: { stage: "draft" | "final"; due: string };
  /** 굵은 진행 상태 (예: 수락 대기, 완료) */
  status?: string;
  onOpen: () => void;
}

/** 탐색 목록 카드 (다른 가게의 제안·의뢰) */
function ExploreCard({
  kind,
  title,
  field,
  storeName,
  empathyCount,
  deadline,
  status,
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
        <CategoryBadge field={field} />
        <span className="explore-card__store">{storeName}</span>
      </div>
      {status && <p className="explore-card__status">{status}</p>}
      <div className="explore-card__divider" />
      <TextButton className="explore-card__open" onClick={onOpen}>
        {kind === "proposal" ? "제안서 상세 보기" : "의뢰서 상세 보기"}
      </TextButton>
    </article>
  );
}

export default ExploreCard;

import "./DeadlineBadge.css";

interface DeadlineBadgeProps {
  /** draft = 초안 제출 전, final = 초안 제출 후 */
  stage: "draft" | "final";
  /** 예: ~9/27 (formatDueShort) */
  due: string;
}

/** 마감 뱃지. 칩 없이 점 + 단계 + 「~월/일」 (초안 = 노란 점, 최종 = 빨간 점, ADR 0082) */
function DeadlineBadge({ stage, due }: DeadlineBadgeProps) {
  return (
    <span className={`deadline-badge deadline-badge--${stage}`}>
      <span className="deadline-badge__stage">{stage === "draft" ? "초안" : "최종"}</span>
      <span className="deadline-badge__due">{due}</span>
    </span>
  );
}

export default DeadlineBadge;

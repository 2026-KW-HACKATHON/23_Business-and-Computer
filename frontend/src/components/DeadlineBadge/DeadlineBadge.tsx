import "./DeadlineBadge.css";

interface DeadlineBadgeProps {
  /** draft = 초안 제출 전, final = 초안 제출 후 */
  stage: "draft" | "final";
  /** 예: 9월 27일까지 */
  due: string;
}

/** 마감 뱃지. 날짜 아래에 형광펜을 칠한다 (초안 = 연노랑, 최종 = 진한 노랑) */
function DeadlineBadge({ stage, due }: DeadlineBadgeProps) {
  return (
    <span className={`deadline-badge deadline-badge--${stage}`}>
      <span className="deadline-badge__stage">{stage === "draft" ? "초안" : "최종"}</span>
      <span className="deadline-badge__due">{due}</span>
    </span>
  );
}

export default DeadlineBadge;

import "./LoadingDots.css";

interface LoadingDotsProps {
  /** 화면 낭독기가 읽는 글 (예: 보내는 중). 없으면 옆 글이 알리는 것으로 보고 읽지 않는다 */
  label?: string;
  /** gray = 로딩 회색, current = 글자색 (버튼 안) */
  tone?: "gray" | "current";
  className?: string;
}

/** 점 세 개가 1 → 2 → 3 차례로 숨 쉬는 작은 로딩. 숫자 자리 · 목록 끝 · 버튼 안에 쓴다 */
function LoadingDots({ label, tone = "gray", className = "" }: LoadingDotsProps) {
  return (
    <span
      className={`loading-dots loading-dots--${tone} ${className}`.trim()}
      role={label ? "status" : undefined}
      aria-hidden={label ? undefined : true}
    >
      <span className="loading-dots__dot" aria-hidden="true" />
      <span className="loading-dots__dot" aria-hidden="true" />
      <span className="loading-dots__dot" aria-hidden="true" />
      {label && <span className="loading-dots__sr">{label}</span>}
    </span>
  );
}

export default LoadingDots;

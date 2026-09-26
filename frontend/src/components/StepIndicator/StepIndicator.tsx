import type { Role } from "../../types/role";
import "./StepIndicator.css";

interface StepIndicatorProps {
  total: number;
  /** 1부터 시작. current 칸까지 색이 채워진다 */
  current: number;
  tone?: Role;
}

function StepIndicator({ total, current, tone = "owner" }: StepIndicatorProps) {
  return (
    <div
      className={`step-indicator step-indicator--${tone}`}
      role="progressbar"
      aria-valuemin={1}
      aria-valuemax={total}
      aria-valuenow={current}
    >
      {Array.from({ length: total }, (_, i) => (
        <span
          key={i}
          className={`step-indicator__bar${i < current ? " step-indicator__bar--done" : ""}`}
        />
      ))}
    </div>
  );
}

export default StepIndicator;

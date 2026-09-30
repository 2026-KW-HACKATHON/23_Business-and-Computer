import type { Role } from "../../types/role";
import "./FlowBar.css";

/** 지남 / 지금 / 아직 / 건너뜀 */
export type FlowStepState = "done" | "current" | "todo" | "skipped";

export interface FlowStep {
  label: string;
  /** 단계 이름 아래 작은 글자 (예: 29일까지) */
  sub?: string;
  state: FlowStepState;
}

interface FlowBarProps {
  steps: FlowStep[];
  /** 지금 단계의 색. 사장님 = 노랑, 학생 = 자주 */
  tone?: Role;
}

/** 흐름 막대 (예: 의뢰 → 시작 → 초안 → 수정 → 완료) */
function FlowBar({ steps, tone = "owner" }: FlowBarProps) {
  // 도달한 마지막 단계까지는 잇는 선을 검정으로 그린다
  const lastReached = steps.reduce(
    (last, step, i) => (step.state === "done" || step.state === "current" ? i : last),
    -1,
  );

  return (
    <ol className={`flow-bar flow-bar--${tone}`}>
      {steps.map((step, i) => (
        <li
          key={step.label}
          className={[
            "flow-bar__step",
            `flow-bar__step--${step.state}`,
            i > 0 && i <= lastReached ? "flow-bar__step--linked" : "",
          ]
            .filter(Boolean)
            .join(" ")}
          aria-current={step.state === "current" ? "step" : undefined}
        >
          <span className="flow-bar__circle" aria-hidden="true">
            {step.state === "done" && (
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path
                  d="M2.5 6.2L4.9 8.5L9.5 3.5"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            )}
          </span>
          <span className="flow-bar__label">{step.label}</span>
          {step.sub && <span className="flow-bar__sub">{step.sub}</span>}
        </li>
      ))}
    </ol>
  );
}

export default FlowBar;

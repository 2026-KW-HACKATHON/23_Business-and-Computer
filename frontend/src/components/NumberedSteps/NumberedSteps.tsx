import type { ReactNode } from "react";
import "./NumberedSteps.css";

export interface NumberedStep {
  title: ReactNode;
  description?: string;
  /** 오른쪽 굵은 글자 (예: 전액 환불) */
  trailing?: string;
  /** 오른쪽 글자를 빨강으로 (예: 취소 불가) */
  danger?: boolean;
  /** 번호 대신 쓸 글자 (예: !). 회색 원으로 보인다 */
  mark?: string;
}

interface NumberedStepsProps {
  steps: NumberedStep[];
  /** card = 흰 상자 안에 줄마다 구분선 (취소·환불 안내) */
  variant?: "plain" | "card";
}

/** 연노랑 번호 원 + 설명 목록 (예: 안전결제는 이렇게 진행돼요) */
function NumberedSteps({ steps, variant = "plain" }: NumberedStepsProps) {
  return (
    <ol className={`numbered-steps numbered-steps--${variant}`}>
      {steps.map((step, i) => (
        <li key={i} className="numbered-steps__item">
          <span
            className={`numbered-steps__mark${step.mark ? " numbered-steps__mark--muted" : ""}`}
            aria-hidden="true"
          >
            {step.mark ?? i + 1}
          </span>
          <span className="numbered-steps__text">
            <span className="numbered-steps__title">{step.title}</span>
            {step.description && (
              <span className="numbered-steps__description">{step.description}</span>
            )}
          </span>
          {step.trailing && (
            <span
              className={`numbered-steps__trailing${
                step.danger ? " numbered-steps__trailing--danger" : ""
              }`}
            >
              {step.trailing}
            </span>
          )}
        </li>
      ))}
    </ol>
  );
}

export default NumberedSteps;

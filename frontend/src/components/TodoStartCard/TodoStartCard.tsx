import type { ReactNode } from "react";
import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import Button from "../Button/Button";
import "./TodoStartCard.css";

interface TodoStartCardProps {
  tone: Role;
  /** 「첫 의뢰를\n올려 볼까요?」 */
  title: string;
  /** 「의뢰나 제안이 생기면\n여기서 확인해요」 */
  description: string;
  /** 「골목인턴은 이렇게 써요」 단계. 굵은 말은 <strong> */
  steps: ReactNode[];
  /** 「첫 의뢰 올리기」 */
  actionLabel: string;
  onAction: () => void;
}

/**
 * 피그마 「확인할 일 카드 넘기기」 현재=처음 (ADR 0051). 이력이 하나도 없는 홈의 확인할 일 자리에 놓는다:
 * 첫 할 일과 손 흔드는 캐릭터, 「골목인턴은 이렇게 써요」 세 단계, 첫 할 일 버튼
 */
function TodoStartCard({ tone, title, description, steps, actionLabel, onAction }: TodoStartCardProps) {
  return (
    <div className={`todo-start-card todo-start-card--${tone}`}>
      <div className="todo-start-card__top">
        <div className="todo-start-card__text">
          <h3 className="todo-start-card__title">{title}</h3>
          <p className="todo-start-card__description">{description}</p>
        </div>
        <AppImage name={tone === "owner" ? "firstVisitOwner" : "firstVisitStudent"} priority />
      </div>
      <div className="todo-start-card__divider" />
      <h4 className="todo-start-card__steps-title">골목인턴은 이렇게 써요</h4>
      <ol className="todo-start-card__steps">
        {steps.map((step, i) => (
          <li key={i} className="todo-start-card__step">
            <span className="todo-start-card__number" aria-hidden="true">
              {i + 1}
            </span>
            <span>{step}</span>
          </li>
        ))}
      </ol>
      <Button tone={tone} fullWidth onClick={onAction}>
        {actionLabel}
      </Button>
    </div>
  );
}

export default TodoStartCard;

import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import "./TodoNoneCard.css";

/**
 * 피그마 「확인할 일 카드 넘기기」 현재=없음 (ADR 0051). 이력은 있지만 지금 확인할 일이 없을 때 확인할 일
 * 자리에 놓는다: 「지금 확인할 일이 없어요」와 원 위의 따봉(사장님) · 브이(학생) 캐릭터
 */
function TodoNoneCard({ tone }: { tone: Role }) {
  return (
    <div className={`todo-none-card todo-none-card--${tone}`}>
      <div className="todo-none-card__text">
        <h3 className="todo-none-card__title">{"지금 확인할 일이\n없어요"}</h3>
        <p className="todo-none-card__description">{"할 일이 생기면\n바로 알려 드릴게요"}</p>
      </div>
      <div className="todo-none-card__picture" aria-hidden="true">
        <AppImage name={tone === "owner" ? "doneOwnerThumbsUp" : "doneStudentV"} width={100} alt="" />
      </div>
    </div>
  );
}

export default TodoNoneCard;

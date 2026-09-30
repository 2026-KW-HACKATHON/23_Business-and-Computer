import type { Role } from "../../types/role";
import "./TurnNotice.css";

interface TurnNoticeProps {
  /** 사장님 = 노랑, 학생 = 자주 */
  tone: Role;
  title: string;
  description?: string;
}

/** 「내 차례」 알림. 지금 내가 해야 할 일을 역할 색 테두리로 알린다 */
function TurnNotice({ tone, title, description }: TurnNoticeProps) {
  return (
    <div className={`turn-notice turn-notice--${tone}`} role="status">
      <span className="turn-notice__badge">내 차례</span>
      <div className="turn-notice__text">
        <strong className="turn-notice__title">{title}</strong>
        {description && <p className="turn-notice__description">{description}</p>}
      </div>
    </div>
  );
}

export default TurnNotice;

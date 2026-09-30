import type { Role } from "../../types/role";
import "./DemoRoleBadge.css";

interface DemoRoleBadgeProps {
  /** 지금 둘러보는 역할. 누르면 다른 역할로 바뀐다 */
  role: Role;
  onClick: () => void;
}

/** 둘러보기(데모) 모드에서만 홈 앱바 로고 옆에 보이는 「둘러보기 중 · ○○으로 보기 ⇄」 */
function DemoRoleBadge({ role, onClick }: DemoRoleBadgeProps) {
  const target = role === "owner" ? "학생" : "사장님";

  return (
    <button type="button" className="demo-role-badge" onClick={onClick}>
      <span className="demo-role-badge__dot" aria-hidden="true" />
      <span className="demo-role-badge__status">둘러보기 중</span>
      <span aria-hidden="true">·</span>
      <span className="demo-role-badge__switch">{target}으로 보기 ⇄</span>
    </button>
  );
}

export default DemoRoleBadge;

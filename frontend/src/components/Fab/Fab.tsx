import type { Role } from "../../types/role";
import "./Fab.css";

interface FabProps {
  /** 사장님 = 새 의뢰(노랑), 학생 = 새 제안(자주) */
  role: Role;
  /** 스크롤을 내리면 true. 글자를 숨기고 동그라미로 접힌다 */
  collapsed?: boolean;
  onClick: () => void;
  className?: string;
}

/** 플로팅 버튼 「+ 새 의뢰」 · 「+ 새 제안」 */
function Fab({ role, collapsed = false, onClick, className = "" }: FabProps) {
  const label = role === "owner" ? "새 의뢰" : "새 제안";
  const classes = ["fab", `fab--${role}`, collapsed ? "fab--collapsed" : "", className]
    .filter(Boolean)
    .join(" ");

  return (
    <button type="button" className={classes} onClick={onClick} aria-label={label}>
      <svg className="fab__plus" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path d="M10 4v12M4 10h12" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" />
      </svg>
      {!collapsed && <span>{label}</span>}
    </button>
  );
}

export default Fab;

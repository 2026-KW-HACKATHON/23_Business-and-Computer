import type { Role } from "../../types/role";
import "./DemoRoleBadge.css";

interface DemoRoleBadgeProps {
  /** 지금 둘러보는 역할. 누르면 다른 역할로 바뀐다 */
  role: Role;
  onClick: () => void;
}

/**
 * 「⇄」 모양 화살표 두 개. 마우스를 올리거나 누르면 위는 오른쪽, 아래는 왼쪽으로 살짝 밀려 서로 바뀌는 느낌을 준다.
 * 둘러보기 첫 안내(DemoGuide)의 밝게 보이는 뱃지도 같은 모양을 쓴다
 */
export function SwitchArrows() {
  return (
    <svg className="demo-role-badge__arrows" width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
      <path className="demo-role-badge__arrow demo-role-badge__arrow--right" d="M2 4h7.5M7.5 2 9.5 4l-2 2" />
      <path className="demo-role-badge__arrow demo-role-badge__arrow--left" d="M10 8H2.5M4.5 6l-2 2 2 2" />
    </svg>
  );
}

/** 둘러보기(데모) 모드에서만 홈 앱바 로고 옆에 보이는 「○○으로 보기 ⇄」. 「둘러보기 중」은 화면 위 띠가 보여 준다 */
function DemoRoleBadge({ role, onClick }: DemoRoleBadgeProps) {
  const target = role === "owner" ? "학생" : "사장님";

  return (
    <button type="button" className="demo-role-badge" onClick={onClick} data-guide="demo-badge">
      <span className="demo-role-badge__dot" aria-hidden="true" />
      <span className="demo-role-badge__switch">{target}으로 보기</span>
      <SwitchArrows />
    </button>
  );
}

export default DemoRoleBadge;

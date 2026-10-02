import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import DemoRoleBadge from "../DemoRoleBadge/DemoRoleBadge";
import "./MainAppBar.css";

interface MainAppBarProps {
  role: Role;
  /** 넣으면 로고 대신 제목을 쓴다 (예: 탐색, 채팅) */
  title?: string;
  /** 안 읽은 알림이 1개 이상이면 종 오른쪽 위에 역할 색 점 */
  hasUnread?: boolean;
  onNotifications: () => void;
  onMy: () => void;
  /** 둘러보기(데모) 모드에서만 넣는다. 로고 옆에 역할 전환 뱃지가 생긴다 */
  onSwitchDemoRole?: () => void;
}

/** 메인 탭(홈 · 탐색 · 채팅) 화면 위 앱바. 회색 배경 + 로고나 제목 + 알림 · MY */
function MainAppBar({
  role,
  title,
  hasUnread = false,
  onNotifications,
  onMy,
  onSwitchDemoRole,
}: MainAppBarProps) {
  return (
    <header className={`main-app-bar main-app-bar--${role}`}>
      <div className="main-app-bar__row">
        {title ? (
          <h1 className="main-app-bar__title">{title}</h1>
        ) : (
          <span className="main-app-bar__logo">
            <AppImage name="logoGakkum" width={54} priority />
            <AppImage name={role === "owner" ? "miniOwner" : "miniStudent"} alt="" priority />
          </span>
        )}
        {onSwitchDemoRole && <DemoRoleBadge role={role} onClick={onSwitchDemoRole} />}
        <div className="main-app-bar__actions">
          <button
            type="button"
            className={`main-app-bar__icon${hasUnread ? " main-app-bar__icon--unread" : ""}`}
            onClick={onNotifications}
            aria-label={hasUnread ? "알림 (안 읽은 알림 있음)" : "알림"}
          >
            <AppImage name="iconBell" alt="" priority />
          </button>
          <button type="button" className="main-app-bar__icon" onClick={onMy} aria-label="내 정보">
            <AppImage name="iconMy" alt="" priority />
          </button>
        </div>
      </div>
    </header>
  );
}

export default MainAppBar;

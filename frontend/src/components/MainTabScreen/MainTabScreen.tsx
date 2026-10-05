import { useState } from "react";
import type { ReactNode, UIEvent } from "react";
import type { Role } from "../../types/role";
import Fab from "../Fab/Fab";
import MainAppBar from "../MainAppBar/MainAppBar";
import TabBar from "../TabBar/TabBar";
import type { MainTab } from "../TabBar/TabBar";
import "./MainTabScreen.css";

interface MainTabScreenProps {
  role: Role;
  tab: MainTab;
  /** 넣으면 앱바에 로고 대신 제목 */
  title?: string;
  hasUnread?: boolean;
  onNotifications: () => void;
  onMy: () => void;
  onSelectTab: (tab: MainTab) => void;
  /** 넣으면 「+ 새 의뢰」 · 「+ 새 제안」 플로팅 버튼을 띄운다 */
  onFab?: () => void;
  /** 둘러보기 중인 홈에서만 넣는다. 앱바 로고 옆에 역할 전환 배지가 생긴다 */
  onSwitchDemoRole?: () => void;
  children: ReactNode;
}

/**
 * 메인 탭(홈 · 탐색 · 채팅) 화면 틀. 앱바는 고정이고 가운데만 스크롤된다.
 * 플로팅 버튼과 탭바는 아래에 떠 있고, 스크롤을 내리면 플로팅 버튼이 동그라미로 접힌다.
 */
function MainTabScreen({
  role,
  tab,
  title,
  hasUnread,
  onNotifications,
  onMy,
  onSelectTab,
  onFab,
  onSwitchDemoRole,
  children,
}: MainTabScreenProps) {
  const [scrolled, setScrolled] = useState(false);

  const handleScroll = (e: UIEvent<HTMLElement>) => {
    setScrolled(e.currentTarget.scrollTop > 0);
  };

  return (
    <div className="main-tab-screen">
      <MainAppBar
        role={role}
        title={title}
        hasUnread={hasUnread}
        onNotifications={onNotifications}
        onMy={onMy}
        onSwitchDemoRole={onSwitchDemoRole}
      />
      <main className="main-tab-screen__scroll" onScroll={handleScroll}>
        {children}
      </main>
      {onFab && (
        <Fab role={role} collapsed={scrolled} onClick={onFab} className="main-tab-screen__fab" />
      )}
      <TabBar current={tab} onSelect={onSelectTab} className="main-tab-screen__tab-bar" />
    </div>
  );
}

export default MainTabScreen;

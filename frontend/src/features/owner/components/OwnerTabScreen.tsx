import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { MainTabScreen } from "../../../components";
import { useDemoRoleSwitch } from "../../auth";
import { useChatUnread } from "../../chat";
import { useOwnerNotifications } from "../hooks/useOwnerData";
import type { MainTab } from "../../../components";
import { OWNER_PATHS, OWNER_TAB_PATHS } from "../lib/paths";

interface OwnerTabScreenProps {
  tab: MainTab;
  /** 넣으면 앱바에 로고 대신 제목 */
  title?: string;
  /** 「+ 새 의뢰」 플로팅 버튼을 띄울지 */
  showFab?: boolean;
  children: ReactNode;
}

/** 사장님 메인 탭 틀. 탭 · 알림 · MY · 새 의뢰 버튼을 사장님 화면 주소로 잇는다 */
function OwnerTabScreen({ tab, title, showFab = false, children }: OwnerTabScreenProps) {
  const navigate = useNavigate();
  // 둘러보기 중이면 홈 앱바에 「학생으로 보기 ⇄」
  const switchDemoRole = useDemoRoleSwitch("owner");
  // 홈 · 탐색 · 채팅 어느 탭에서든 안 읽은 알림이 있으면 종에 점
  // 탭바 「채팅」 점: 안 읽은 채팅 메시지가 있는지
  const hasUnreadChat = useChatUnread();
  const hasUnread = useOwnerNotifications().some((n) => !n.read);

  return (
    <MainTabScreen
      role="owner"
      tab={tab}
      title={title}
      hasUnread={hasUnread}
      hasUnreadChat={hasUnreadChat}
      onNotifications={() => navigate(OWNER_PATHS.notifications)}
      onMy={() => navigate(OWNER_PATHS.me)}
      onSwitchDemoRole={tab === "home" ? switchDemoRole : undefined}
      onSelectTab={(next) => {
        if (next !== tab) navigate(OWNER_TAB_PATHS[next], { replace: true });
      }}
      onFab={showFab ? () => navigate(OWNER_PATHS.newRequest) : undefined}
    >
      {children}
    </MainTabScreen>
  );
}

export default OwnerTabScreen;

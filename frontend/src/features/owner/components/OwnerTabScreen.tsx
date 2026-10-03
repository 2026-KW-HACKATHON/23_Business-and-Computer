import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { MainTabScreen } from "../../../components";
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
  // 홈 · 탐색 · 채팅 어느 탭에서든 안 읽은 알림이 있으면 종에 점
  const hasUnread = useOwnerNotifications().some((n) => !n.read);

  return (
    <MainTabScreen
      role="owner"
      tab={tab}
      title={title}
      hasUnread={hasUnread}
      onNotifications={() => navigate(OWNER_PATHS.notifications)}
      onMy={() => navigate(OWNER_PATHS.me)}
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

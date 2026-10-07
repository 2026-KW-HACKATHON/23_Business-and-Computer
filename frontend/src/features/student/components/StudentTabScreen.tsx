import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { MainTabScreen } from "../../../components";
import { useDemoRoleSwitch } from "../../auth";
import { useChatUnread } from "../../chat";
import { useStudentNotifications } from "../hooks/useStudentData";
import type { MainTab } from "../../../components";
import { STUDENT_PATHS, STUDENT_TAB_PATHS } from "../lib/paths";

interface StudentTabScreenProps {
  tab: MainTab;
  /** 넣으면 앱바에 로고 대신 제목 */
  title?: string;
  /** 「+ 새 제안」 플로팅 버튼을 띄울지 */
  showFab?: boolean;
  children: ReactNode;
}

/** 학생 메인 탭 틀. 탭 · 알림 · MY · 새 제안 버튼을 학생 화면 주소로 잇는다 */
function StudentTabScreen({ tab, title, showFab = false, children }: StudentTabScreenProps) {
  const navigate = useNavigate();
  // 둘러보기 중이면 홈 앱바에 「둘러보기 중 · 사장님으로 보기 ⇄」
  const switchDemoRole = useDemoRoleSwitch("student");
  // 홈 · 탐색 · 채팅 어느 탭에서든 안 읽은 알림이 있으면 종에 점
  // 탭바 「채팅」 점: 안 읽은 채팅 메시지가 있는지
  const hasUnreadChat = useChatUnread();
  const hasUnread = useStudentNotifications().some((n) => !n.read);

  return (
    <MainTabScreen
      role="student"
      tab={tab}
      title={title}
      hasUnread={hasUnread}
      hasUnreadChat={hasUnreadChat}
      onNotifications={() => navigate(STUDENT_PATHS.notifications)}
      onMy={() => navigate(STUDENT_PATHS.me)}
      onSwitchDemoRole={tab === "home" ? switchDemoRole : undefined}
      onSelectTab={(next) => {
        if (next !== tab) navigate(STUDENT_TAB_PATHS[next], { replace: true });
      }}
      onFab={showFab ? () => navigate(STUDENT_PATHS.newProposal) : undefined}
    >
      {children}
    </MainTabScreen>
  );
}

export default StudentTabScreen;

import { useNavigate } from "react-router-dom";
import { NotificationRow, SubScreen, TextButton, WorkKindIcon } from "../components";
import {
  NOTIFICATION_ICON,
  STUDENT_PATHS,
  notificationPath,
  markNotificationsRead,
  notificationState,
  useStudentNotifications,
} from "../features/student";
import type { StudentNotification } from "../features/student";
import { useBack } from "../hooks/useBack";
import { daysAgo, formatNotificationTime } from "../lib/date";
import "./StudentNotificationsPage.css";

const GROUPS = ["오늘", "어제", "이전"] as const;

const groupOf = (n: StudentNotification) => {
  const days = daysAgo(n.createdAt);
  return days <= 0 ? "오늘" : days === 1 ? "어제" : "이전";
};

/** 피그마 「알림 (학생)」. 오늘 / 어제 / 이전으로 묶고, 누르면 그 알림의 화면으로 간다 */
function StudentNotificationsPage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const notifications = useStudentNotifications();
  // 읽음 표시는 백엔드 연동 전까지 새로고침하면 처음으로 돌아간다
  const isUnread = (n: StudentNotification) => !n.read;

  const open = (n: StudentNotification) => {
    markNotificationsRead([n.id]);
    navigate(notificationPath(n), { state: notificationState(n) });
  };

  const readAll = () => markNotificationsRead(notifications.map((n) => n.id));

  return (
    <SubScreen
      title="알림"
      onBack={back}
      right={
        <TextButton showChevron={false} onClick={readAll}>
          모두 읽음
        </TextButton>
      }
    >
      {notifications.length === 0 && (
        <p className="student-notifications__empty">아직 알림이 없어요</p>
      )}
      {GROUPS.map((group) => {
        const items = notifications.filter((n) => groupOf(n) === group);
        if (items.length === 0) return null;
        return (
          <section key={group} className="student-notifications__group">
            <h2 className="student-notifications__date">{group}</h2>
            <ul className="student-notifications__list">
              {items.map((n) => {
                const icon = NOTIFICATION_ICON[n.type];
                return (
                  <li key={n.id}>
                    <NotificationRow
                      tone="student"
                      icon={
                        icon === "proposal" || icon === "request" ? (
                          <WorkKindIcon kind={icon} size={22} />
                        ) : (
                          icon
                        )
                      }
                      title={n.title}
                      body={n.body}
                      time={formatNotificationTime(n.createdAt)}
                      unread={isUnread(n)}
                      onClick={() => open(n)}
                    />
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}
    </SubScreen>
  );
}

export default StudentNotificationsPage;

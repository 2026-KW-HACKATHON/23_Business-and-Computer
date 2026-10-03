import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { NotificationRow, SubScreen, TextButton, WorkKindIcon } from "../components";
import {
  NOTIFICATION_ICON,
  OWNER_PATHS,
  notificationPath,
  useOwnerNotifications,
} from "../features/owner";
import type { OwnerNotification } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { daysAgo, formatNotificationTime } from "../lib/date";
import "./OwnerNotificationsPage.css";

const GROUPS = ["오늘", "어제", "이전"] as const;

const groupOf = (n: OwnerNotification) => {
  const days = daysAgo(n.createdAt);
  return days <= 0 ? "오늘" : days === 1 ? "어제" : "이전";
};

/** 피그마 「알림 (사장님)」. 오늘 / 어제 / 이전으로 묶고, 누르면 그 알림의 화면으로 간다 */
function OwnerNotificationsPage() {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const notifications = useOwnerNotifications();
  // 읽음 표시는 백엔드 연동 전까지 이 화면 안에서만 기억한다
  const [readIds, setReadIds] = useState<Set<string>>(new Set());
  const isUnread = (n: OwnerNotification) => !n.read && !readIds.has(n.id);

  const open = (n: OwnerNotification) => {
    setReadIds((ids) => new Set(ids).add(n.id));
    navigate(notificationPath(n));
  };

  const readAll = () => setReadIds(new Set(notifications.map((n) => n.id)));

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
      {notifications.length === 0 && <p className="owner-notifications__empty">아직 알림이 없어요</p>}
      {GROUPS.map((group) => {
        const items = notifications.filter((n) => groupOf(n) === group);
        if (items.length === 0) return null;
        return (
          <section key={group} className="owner-notifications__group">
            <h2 className="owner-notifications__date">{group}</h2>
            <ul className="owner-notifications__list">
              {items.map((n) => {
                const icon = NOTIFICATION_ICON[n.type];
                return (
                  <li key={n.id}>
                    <NotificationRow
                      tone="owner"
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

export default OwnerNotificationsPage;

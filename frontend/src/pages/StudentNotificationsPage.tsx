import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { LoadNotice, NotificationRow, SubScreen, TextButton, WorkKindIcon } from "../components";
import { useLoadMoreSentinel } from "../features/explore";
import {
  NOTIFICATION_GROUPS,
  notificationGroupOf,
  notificationIcon,
  useNotifications,
} from "../features/notification";
import type { NotificationItem } from "../features/notification";
import { STUDENT_PATHS, resolveNotificationPath } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatNotificationTime } from "../lib/date";
import "./StudentNotificationsPage.css";

/**
 * 피그마 「알림 (학생)」. 받은 알림 (GET /me/notifications) 을 오늘 / 어제 / 이전으로 묶고,
 * 아래로 내리면 다음 쪽을 불러온다. 누르면 읽음으로 바꾸고 그 알림의 화면으로 간다 (갈 곳이 없으면 그대로).
 * 새 채팅 메시지 알림은 목록에 넣지 않는다 (채팅 탭 점으로 안내).
 */
function StudentNotificationsPage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const { load, reload, loadMore, markRead, markAllRead } = useNotifications();
  const sentinel = useLoadMoreSentinel(
    load.status === "loaded" && load.nextCursor !== null && load.more === "idle",
    loadMore,
  );

  // 수락된 제안 알림은 갈 곳을 보낸 제안에서 찾아서, 그사이 화면을 떠났으면 이동하지 않는다
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const open = (item: NotificationItem) => {
    if (!item.read) markRead(item.id);
    void resolveNotificationPath(item).then((path) => {
      if (path && mounted.current) navigate(path);
    });
  };

  return (
    <SubScreen
      title="알림"
      onBack={back}
      right={
        load.status === "loaded" && (
          <TextButton showChevron={false} onClick={markAllRead}>
            모두 읽음
          </TextButton>
        )
      }
    >
      {load.status !== "loaded" ? (
        <LoadNotice
          status={load.status}
          loadingText="알림을 불러오는 중이에요"
          errorText="알림을 불러오지 못했어요"
          onRetry={reload}
        />
      ) : (
        <>
          {load.items.length === 0 && load.nextCursor === null && (
            <p className="student-notifications__empty">아직 알림이 없어요</p>
          )}
          {NOTIFICATION_GROUPS.map((group) => {
            const items = load.items.filter((item) => notificationGroupOf(item) === group);
            if (items.length === 0) return null;
            return (
              <section key={group} className="student-notifications__group">
                <h2 className="student-notifications__date">{group}</h2>
                <ul className="student-notifications__list">
                  {items.map((item) => {
                    const icon = notificationIcon(item.type);
                    return (
                      <li key={item.id}>
                        <NotificationRow
                          tone="student"
                          icon={
                            icon === "proposal" || icon === "request" ? (
                              <WorkKindIcon kind={icon} size={22} />
                            ) : (
                              icon
                            )
                          }
                          title={item.title}
                          body={item.body}
                          time={formatNotificationTime(item.createdAt)}
                          unread={!item.read}
                          onClick={() => open(item)}
                        />
                      </li>
                    );
                  })}
                </ul>
              </section>
            );
          })}
          {load.nextCursor !== null && <div ref={sentinel} aria-hidden="true" />}
          {load.more !== "idle" && (
            <LoadNotice
              status={load.more}
              loadingText="더 불러오는 중이에요"
              errorText="더 불러오지 못했어요"
              onRetry={loadMore}
            />
          )}
        </>
      )}
    </SubScreen>
  );
}

export default StudentNotificationsPage;

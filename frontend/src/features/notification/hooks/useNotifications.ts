import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchNotifications, markAllNotificationsRead, markNotificationRead } from "../api/notificationApi";
import type { NotificationResponse } from "../api/notificationApi";
import { isHiddenNotification, notificationFailureOf, toNotificationItem } from "../lib/notifications";
import type { NotificationItem } from "../lib/notifications";
import { trackNotificationRead } from "../lib/readSync";

export type NotificationsLoad =
  | { status: "loading" }
  | { status: "error" }
  | {
      status: "loaded";
      items: NotificationItem[];
      /** 다음 쪽 커서. 없으면 끝 */
      nextCursor: string | null;
      /** 다음 쪽 불러오기 */
      more: "idle" | "loading" | "error";
    };

/** 목록에 넣는 알림만 (새 채팅 메시지는 뺀다) */
const visibleItems = (responses: NotificationResponse[]) =>
  responses.map(toNotificationItem).filter((item) => !isHiddenNotification(item));

/** 이미 있는 알림은 다시 넣지 않는다 */
const appendItems = (current: NotificationItem[], next: NotificationItem[]) => {
  const ids = new Set(current.map((item) => item.id));
  return [...current, ...next.filter((item) => !ids.has(item.id))];
};

/**
 * 받은 알림 (GET /me/notifications, 최신순 · 커서). 첫 쪽은 화면에 들어올 때, 다음 쪽은 loadMore 로.
 * 하나 읽음 · 모두 읽음은 화면에 먼저 반영하고 서버에 보낸다 (모두 읽음이 실패하면 다시 불러온다).
 * 401 은 /login 으로 보낸다.
 */
export function useNotifications(): {
  load: NotificationsLoad;
  reload: () => void;
  loadMore: () => void;
  markRead: (notificationId: number) => void;
  markAllRead: () => void;
} {
  const navigate = useNavigate();
  const [load, setLoad] = useState<NotificationsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    void fetchNotifications(null).then(
      (page) => {
        if (active) {
          setLoad({ status: "loaded", items: visibleItems(page.items), nextCursor: page.nextCursor, more: "idle" });
        }
      },
      (error: unknown) => {
        if (!active) return;
        if (notificationFailureOf(error) === "unauthorized") navigate("/login", { replace: true });
        else setLoad({ status: "error" });
      },
    );
    return () => {
      active = false;
    };
  }, [request, navigate]);

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  const loadMore = useCallback(() => {
    if (load.status !== "loaded" || !load.nextCursor || load.more === "loading") return;
    const cursor = load.nextCursor;
    // 그사이 다시 불러왔으면 (커서가 바뀌었으면) 늦게 온 답은 버린다
    const sameCursor = (current: NotificationsLoad) =>
      current.status === "loaded" && current.nextCursor === cursor;
    setLoad({ ...load, more: "loading" });
    void fetchNotifications(cursor).then(
      (page) => {
        setLoad((current) =>
          current.status === "loaded" && sameCursor(current)
            ? {
                ...current,
                items: appendItems(current.items, visibleItems(page.items)),
                nextCursor: page.nextCursor,
                more: "idle",
              }
            : current,
        );
      },
      (error: unknown) => {
        if (notificationFailureOf(error) === "unauthorized") {
          navigate("/login", { replace: true });
          return;
        }
        setLoad((current) => (current.status === "loaded" && sameCursor(current) ? { ...current, more: "error" } : current));
      },
    );
  }, [load, navigate]);

  const markRead = useCallback((notificationId: number) => {
    setLoad((current) =>
      current.status === "loaded"
        ? {
            ...current,
            items: current.items.map((item) => (item.id === notificationId ? { ...item, read: true } : item)),
          }
        : current,
    );
    const sent = markNotificationRead(notificationId);
    trackNotificationRead(sent);
    // 못 읽혔으면 다음에 들어올 때 안 읽음으로 다시 보인다
    void sent.catch(() => undefined);
  }, []);

  const markAllRead = useCallback(() => {
    setLoad((current) =>
      current.status === "loaded"
        ? { ...current, items: current.items.map((item) => ({ ...item, read: true })) }
        : current,
    );
    const sent = markAllNotificationsRead();
    trackNotificationRead(sent);
    void sent.catch((error: unknown) => {
      if (notificationFailureOf(error) === "unauthorized") navigate("/login", { replace: true });
      else setRequest((n) => n + 1);
    });
  }, [navigate]);

  return { load, reload, loadMore, markRead, markAllRead };
}

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

/** 한 번에 이어서 불러오는 쪽 수의 한도. 넘으면 목록 끝(아래로 내리기)에서 이어서 불러온다 */
const MAX_PAGES_PER_FETCH = 10;

/**
 * cursor 부터 한 쪽을 불러오고, 목록에 넣을 알림이 하나도 없으면(새 채팅 메시지만 있는 쪽)
 * 보이는 알림이 나오거나 끝날 때까지 다음 쪽을 이어서 불러온다
 */
async function fetchVisiblePage(
  cursor: string | null,
): Promise<{ items: NotificationItem[]; nextCursor: string | null }> {
  let page = await fetchNotifications(cursor);
  let items = visibleItems(page.items);
  for (let fetched = 1; items.length === 0 && page.nextCursor && fetched < MAX_PAGES_PER_FETCH; fetched++) {
    page = await fetchNotifications(page.nextCursor);
    items = visibleItems(page.items);
  }
  return { items, nextCursor: page.nextCursor };
}

/** 이미 있는 알림은 다시 넣지 않는다 */
const appendItems = (current: NotificationItem[], next: NotificationItem[]) => {
  const ids = new Set(current.map((item) => item.id));
  return [...current, ...next.filter((item) => !ids.has(item.id))];
};

/**
 * 받은 알림 (GET /me/notifications, 최신순 · 커서). 첫 쪽은 화면에 들어올 때, 다음 쪽은 loadMore 로.
 * 새 채팅 메시지 알림은 빼고, 빼고 나서 보이는 알림이 없는 쪽은 건너뛰어 다음 쪽을 이어서 불러온다.
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
    void fetchVisiblePage(null).then(
      (page) => {
        if (active) setLoad({ status: "loaded", items: page.items, nextCursor: page.nextCursor, more: "idle" });
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
    void fetchVisiblePage(cursor).then(
      (page) => {
        setLoad((current) =>
          current.status === "loaded" && sameCursor(current)
            ? {
                ...current,
                items: appendItems(current.items, page.items),
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

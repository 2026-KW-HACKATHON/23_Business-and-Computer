import { useEffect, useState } from "react";
import { fetchUnreadNotificationCount } from "../api/notificationApi";
import { notificationReadsSettled, onNotificationReadSettled } from "../lib/readSync";

/** 종 점을 다시 불러오는 간격 */
const POLL_MS = 20_000;

/**
 * 앱바 종 점: 안 읽은 알림이 하나라도 있으면 true (GET /me/notifications/unread-count).
 * 서버가 세는 수라 목록에서 빼는 새 채팅 메시지 알림도 들어간다.
 * 화면에 들어올 때, 탭이 다시 보일 때, 읽음 처리가 끝날 때, 그리고 보이는 동안 20초마다
 * 불러온다. 탭이 안 보이면 멈춘다. 불러오지 못하면 false (점을 숨긴다).
 */
export function useNotificationUnread(): boolean {
  const [unread, setUnread] = useState(false);

  useEffect(() => {
    let active = true;
    let latest = 0;
    let timer: number | undefined;
    const visible = () => document.visibilityState === "visible";

    const refresh = () => {
      window.clearTimeout(timer);
      timer = undefined;
      // 겹쳐 부르면 마지막으로 부른 답만 쓴다
      const call = ++latest;
      void notificationReadsSettled()
        .then(fetchUnreadNotificationCount)
        .then(
          (count) => count > 0,
          () => false,
        )
        .then((next) => {
          if (!active || call !== latest) return;
          setUnread(next);
          if (visible()) timer = window.setTimeout(refresh, POLL_MS);
        });
    };
    const onVisibility = () => {
      if (visible()) {
        refresh();
      } else {
        window.clearTimeout(timer);
        timer = undefined;
      }
    };

    refresh();
    document.addEventListener("visibilitychange", onVisibility);
    const stopListening = onNotificationReadSettled(refresh);
    return () => {
      active = false;
      window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisibility);
      stopListening();
    };
  }, []);

  return unread;
}

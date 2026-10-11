import { useSyncExternalStore } from "react";
import { getUnreadDots, subscribeUnreadDots } from "../lib/unreadDots";
import type { UnreadDots } from "../lib/unreadDots";

/**
 * 앱바 종 점과 탭바 「채팅」 점. 탭 화면들이 같은 답 하나를 나눠 쓴다
 * (GET /me/notifications/unread-count, ADR 0078).
 */
export function useUnreadDots(): UnreadDots {
  return useSyncExternalStore(subscribeUnreadDots, getUnreadDots);
}

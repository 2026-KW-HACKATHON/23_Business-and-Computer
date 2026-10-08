import { apiData } from "../../../api/client";

/**
 * 알림 종류 (NotificationType). 서버가 늘릴 수 있어 모르는 값도 문자열로 받는다
 * (목록에는 보이고, 눌러도 이동하지 않는다)
 */
export type NotificationType =
  | "JOB_DRAFT_SUBMITTED"
  | "PROPOSAL_RECEIVED"
  | "JOB_APPLICATION_RECEIVED"
  | "CHAT_MESSAGE_RECEIVED"
  | "PAYMENT_COMPLETED"
  | "JOB_COMPLETED"
  | "PROPOSAL_LIKE_MILESTONE_REACHED";

/** 누르면 갈 대상의 종류 (NotificationTargetType) */
export type NotificationTargetType = "JOB" | "PROPOSAL" | "CHAT_ROOM" | "PAYMENT";

/** GET /me/notifications 의 알림 하나 */
export interface NotificationResponse {
  id: number;
  type: NotificationType | (string & {});
  /** 서버가 완성한 제목 · 본문 */
  title: string;
  body: string;
  targetType: NotificationTargetType | (string & {});
  /** 의뢰 · 제안 · 결제 id(숫자 글자) 또는 채팅방 id */
  targetId: string;
  /** 읽은 시각. null 이면 안 읽음. 오프셋 없는 UTC */
  readAt?: string | null;
  /** 오프셋 없는 UTC ("2026-10-07T15:22:05") */
  createdAt: string;
}

/** GET /me/notifications 한 쪽. 다음 쪽이 없으면 nextCursor 가 null */
export interface NotificationPageResponse {
  items: NotificationResponse[];
  nextCursor: string | null;
}

/** GET /me/notifications — 받은 알림을 최신순으로 (size 1~100). 읽음 상태는 바뀌지 않는다 */
export async function fetchNotifications(cursor: string | null, size = 20): Promise<NotificationPageResponse> {
  const query = new URLSearchParams({ size: String(size) });
  if (cursor) query.set("cursor", cursor);
  const data = await apiData<Partial<NotificationPageResponse> | undefined>(`/me/notifications?${query}`);
  return { items: data?.items ?? [], nextCursor: data?.nextCursor ?? null };
}

/** GET /me/notifications/unread-count — 안 읽은 알림 수 (모든 종류) */
export async function fetchUnreadNotificationCount(): Promise<number> {
  const data = await apiData<{ unreadCount?: number } | undefined>("/me/notifications/unread-count");
  return data?.unreadCount ?? 0;
}

/** PUT /me/notifications/{id}/read — 하나 읽음. 이미 읽은 알림도 성공한다 */
export async function markNotificationRead(notificationId: number): Promise<void> {
  await apiData<unknown>(`/me/notifications/${notificationId}/read`, { method: "PUT" });
}

/** PUT /me/notifications/read — 안 읽은 알림 모두 읽음. 없어도 성공한다 */
export async function markAllNotificationsRead(): Promise<void> {
  await apiData<unknown>("/me/notifications/read", { method: "PUT" });
}

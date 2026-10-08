import { ApiError } from "../../../api/client";
import { daysAgo, isoOfUtc } from "../../../lib/date";
import type { WorkKind } from "../../../types/workKind";
import type { NotificationResponse } from "../api/notificationApi";

/** 화면에 그리는 알림 하나 */
export interface NotificationItem {
  id: number;
  type: string;
  title: string;
  body: string;
  targetType: string;
  targetId: string;
  read: boolean;
  /** ISO 시각 (Z) */
  createdAt: string;
}

export function toNotificationItem(response: NotificationResponse): NotificationItem {
  return {
    id: response.id,
    type: response.type,
    title: response.title,
    body: response.body,
    targetType: response.targetType,
    targetId: response.targetId,
    read: response.readAt !== null && response.readAt !== undefined,
    createdAt: isoOfUtc(response.createdAt),
  };
}

/** 알림 목록에 넣지 않는 종류. 새 채팅 메시지는 채팅 탭 점으로 안내한다 */
export function isHiddenNotification(item: Pick<NotificationItem, "type">): boolean {
  return item.type === "CHAT_MESSAGE_RECEIVED";
}

export const NOTIFICATION_GROUPS = ["오늘", "어제", "이전"] as const;
export type NotificationGroup = (typeof NOTIFICATION_GROUPS)[number];

export function notificationGroupOf(item: NotificationItem, now = new Date()): NotificationGroup {
  const days = daysAgo(item.createdAt, now);
  return days <= 0 ? "오늘" : days === 1 ? "어제" : "이전";
}

/** 알림 동그라미 안 아이콘: 작업 종류 아이콘 또는 이모지. 모르는 종류는 종 */
export function notificationIcon(type: string): WorkKind | string {
  switch (type) {
    case "JOB_DRAFT_SUBMITTED":
    case "JOB_APPLICATION_RECEIVED":
      return "request";
    case "PROPOSAL_RECEIVED":
    case "PROPOSAL_LIKE_MILESTONE_REACHED":
      return "proposal";
    case "CHAT_MESSAGE_RECEIVED":
      return "💬";
    case "PAYMENT_COMPLETED":
      return "💳";
    case "JOB_COMPLETED":
      return "✅";
    default:
      return "🔔";
  }
}

/** 의뢰 · 제안 · 결제 대상 id (양의 정수 글자). 아니면 undefined */
export function numericTargetId(item: Pick<NotificationItem, "targetId">): string | undefined {
  return /^[1-9][0-9]*$/.test(item.targetId) ? item.targetId : undefined;
}

/** 알림 API 실패. unauthorized 면 다시 로그인 */
export function notificationFailureOf(error: unknown): "unauthorized" | "error" {
  return error instanceof ApiError && error.status === 401 ? "unauthorized" : "error";
}

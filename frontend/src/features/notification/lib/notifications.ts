import { ApiError } from "../../../api/client";
import { daysAgo, isoOfUtc } from "../../../lib/date";
import type { WorkKind } from "../../../types/workKind";
import type { NotificationResponse, NotificationType } from "../api/notificationApi";

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

/**
 * 아는 알림 종류와 동그라미 안 아이콘. 의뢰 · 작업은 의뢰 아이콘, 제안은 제안 아이콘(취소 · 거절도
 * 같은 종류 아이콘), 결제 · 환불 · 정산 💳, 후기 ⭐
 */
const TYPE_ICONS: Record<NotificationType, WorkKind | string> = {
  JOB_DRAFT_SUBMITTED: "request",
  JOB_APPLICATION_RECEIVED: "request",
  JOB_APPLICATION_SELECTED: "request",
  JOB_APPLICATION_REJECTED: "request",
  JOB_STARTED: "request",
  JOB_REVISION_REQUESTED: "request",
  JOB_REVISION_SUBMITTED: "request",
  JOB_RECRUITMENT_CANCELLED: "request",
  JOB_CANCELLED_BY_OWNER: "request",
  PROPOSAL_RECEIVED: "proposal",
  PROPOSAL_LIKE_MILESTONE_REACHED: "proposal",
  PROPOSAL_REJECTED: "proposal",
  PROPOSAL_CANCELLED: "proposal",
  PROPOSAL_ACCEPTED: "proposal",
  CHAT_MESSAGE_RECEIVED: "💬",
  PAYMENT_COMPLETED: "💳",
  PAYMENT_REFUNDED: "💳",
  PAYMENT_SETTLED: "💳",
  JOB_REVIEW_REQUESTED: "⭐",
  JOB_REVIEW_RECEIVED: "⭐",
  JOB_COMPLETED: "✅",
};

const KNOWN_TYPES: ReadonlySet<string> = new Set(Object.keys(TYPE_ICONS));

/** 이 화면이 아는 알림 종류인지. 모르는 종류는 목록에 보이되 눌러도 이동하지 않는다 */
export function isKnownNotificationType(type: string): type is NotificationType {
  return KNOWN_TYPES.has(type);
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
  return isKnownNotificationType(type) ? TYPE_ICONS[type] : "🔔";
}

/** 의뢰 · 제안 · 결제 대상 id (양의 정수 글자). 아니면 undefined */
export function numericTargetId(item: Pick<NotificationItem, "targetId">): string | undefined {
  return /^[1-9][0-9]*$/.test(item.targetId) ? item.targetId : undefined;
}

/** 알림 API 실패. unauthorized 면 다시 로그인 */
export function notificationFailureOf(error: unknown): "unauthorized" | "error" {
  return error instanceof ApiError && error.status === 401 ? "unauthorized" : "error";
}

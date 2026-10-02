import type { WorkKind } from "../../../types/workKind";
import type { NotificationType, OwnerNotification } from "../types";
import { OWNER_PATHS } from "./paths";

/** 알림 동그라미 안 아이콘: 종류 아이콘 또는 이모지 */
export const NOTIFICATION_ICON: Record<NotificationType, WorkKind | string> = {
  DRAFT_SUBMITTED: "request",
  REVISION_SUBMITTED: "request",
  PROPOSAL_RECEIVED: "proposal",
  APPLICATION_RECEIVED: "request",
  CHAT_MESSAGE: "💬",
  PAYMENT_ESCROWED: "💳",
  AUTO_COMPLETE_SOON: "request",
  REVIEW_REQUEST: "⭐",
  WORK_COMPLETED: "✅",
};

/** 알림을 누르면 가는 화면 (노션 「알림 (항목 종류 → 이동)」) */
export function notificationPath({ type, targetId }: OwnerNotification): string {
  switch (type) {
    case "DRAFT_SUBMITTED":
    case "REVISION_SUBMITTED":
    case "AUTO_COMPLETE_SOON":
      return OWNER_PATHS.workCheck(targetId);
    case "PROPOSAL_RECEIVED":
      return OWNER_PATHS.proposal(targetId);
    case "APPLICATION_RECEIVED":
      return OWNER_PATHS.requestApplicants(targetId);
    case "CHAT_MESSAGE":
      return OWNER_PATHS.chat(targetId);
    case "PAYMENT_ESCROWED":
      return OWNER_PATHS.payments;
    case "REVIEW_REQUEST":
      return OWNER_PATHS.workReview(targetId);
    case "WORK_COMPLETED":
      return OWNER_PATHS.workResult(targetId);
  }
}

import type { WorkKind } from "../../../types/workKind";
import type { StudentNotification, StudentNotificationType } from "../types";
import { STUDENT_PATHS } from "./paths";

/** 알림 동그라미 안 아이콘: 종류 아이콘 또는 이모지 */
export const NOTIFICATION_ICON: Record<StudentNotificationType, WorkKind | string> = {
  SELECTED: "request",
  NOT_SELECTED: "request",
  PROPOSAL_ACCEPTED: "proposal",
  EMPATHY_GROWN: "proposal",
  REVISION_REQUESTED: "request",
  CHAT_MESSAGE: "💬",
  SETTLED: "💳",
  DUE_SOON: "⏰",
  REVIEW_RECEIVED: "⭐",
  WORK_CANCELED: "request",
};

/** 알림을 누르면 가는 화면 (피그마 「알림 (학생)」 프로토타입 연결) */
export function notificationPath({ type, targetId }: StudentNotification): string {
  switch (type) {
    case "SELECTED":
    case "DUE_SOON":
      // 초안 제출 화면이 작업 상태에 맞는 화면(수정 요청 확인 등)으로 보낸다
      return STUDENT_PATHS.workSubmit(targetId);
    case "NOT_SELECTED":
      return STUDENT_PATHS.activity("applied");
    case "PROPOSAL_ACCEPTED":
      return STUDENT_PATHS.workStart(targetId);
    case "EMPATHY_GROWN":
      return STUDENT_PATHS.proposal(targetId);
    case "REVISION_REQUESTED":
      return STUDENT_PATHS.workRevision(targetId);
    case "CHAT_MESSAGE":
      return STUDENT_PATHS.chat(targetId);
    case "SETTLED":
      return STUDENT_PATHS.settlements;
    case "REVIEW_RECEIVED":
      return STUDENT_PATHS.workReview(targetId);
    case "WORK_CANCELED":
      return STUDENT_PATHS.workCanceled(targetId);
  }
}

/** 알림에서 들어갈 때 넘길 router state (취소 알림은 팝업부터 띄운다) */
export function notificationState({ type }: StudentNotification): unknown {
  return type === "WORK_CANCELED" ? { notice: true } : undefined;
}

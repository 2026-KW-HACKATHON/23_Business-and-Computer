import { numericTargetId } from "../../notification";
import type { NotificationItem } from "../../notification";
import { STUDENT_PATHS } from "./paths";

/**
 * 학생이 알림을 누르면 가는 화면. 종류에 맞는 화면을 먼저 보고, 없으면(모르는 종류 포함) 대상
 * 종류의 기본 화면으로 간다. 모르는 대상이거나 id 가 맞지 않으면 undefined (목록에 보이되 이동하지 않는다).
 */
export function notificationPath(item: NotificationItem): string | undefined {
  const id = numericTargetId(item);
  if (item.type === "JOB_COMPLETED" && item.targetType === "JOB" && id) {
    return STUDENT_PATHS.workResult(id);
  }
  switch (item.targetType) {
    case "JOB":
      // 초안 제출 화면이 진행 중 작업의 단계(수정 요청 · 확인 중)에 맞는 화면으로 보낸다
      return id ? STUDENT_PATHS.workSubmit(id) : undefined;
    case "PROPOSAL":
      return id ? STUDENT_PATHS.proposal(id) : undefined;
    case "CHAT_ROOM":
      return item.targetId ? STUDENT_PATHS.chat(item.targetId) : undefined;
    case "PAYMENT":
      // 결제 하나를 여는 화면이 없어 정산 내역으로
      return STUDENT_PATHS.settlements;
    default:
      return undefined;
  }
}

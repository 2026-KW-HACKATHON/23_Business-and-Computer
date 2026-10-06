import { formatMonthDay } from "../../../lib/date";
import type { Role } from "../../../types/role";
import type { ApplicationPlan } from "../../../types/workPlan";
import type { ChatProgress, ChatRoom } from "../types";

/**
 * 진행 상태. 작업 상태(jobStatus)가 오면 완료 · 성사되지 않음도 알고, 매칭된 작업은
 * 마감 종류 · 마지막 결과물 검토 상태로 정한다. 알 수 없으면 undefined (그 줄을 숨긴다)
 */
export function chatProgressOf(room: ChatRoom): ChatProgress | undefined {
  if (room.jobStatus === "CLOSED") return { type: "completed" };
  if (room.jobStatus === "CANCELLED") return { type: "notConcluded" };
  if (!room.deadlineType || !room.deadlineDate) return undefined;
  if (room.submissionReviewStatus === "PENDING") return { type: "submitted" };
  const revising = room.deadlineType === "FINAL" || room.submissionReviewStatus === "REVISION_REQUESTED";
  return { type: "making", stage: revising ? "수정안" : "초안", due: room.deadlineDate };
}

/** 채팅 목록의 굵은 진행 상태. 모르면 빈 글자 */
export function chatListStatusText(room: ChatRoom, viewer: Role): string {
  const progress = chatProgressOf(room);
  switch (progress?.type) {
    case "making":
      return `${progress.stage} 만드는 중 (~${formatMonthDay(progress.due)})`;
    case "submitted":
      return viewer === "owner" ? "결과물을 확인해 주세요" : "사장님이 확인 중";
    case "completed":
      return "완료";
    case "notConcluded":
      return "성사되지 않음";
    default:
      return "";
  }
}

/** 채팅방 위 작업 카드의 굵은 진행 상태. 모르면 undefined */
export function chatSummaryText(room: ChatRoom, viewer: Role): string | undefined {
  const progress = chatProgressOf(room);
  switch (progress?.type) {
    case "making":
      return `${progress.stage} 만드는 중, ${formatMonthDay(progress.due)}까지 ${viewer === "owner" ? "도착" : "제출"}`;
    case "submitted":
      return viewer === "owner" ? "결과물이 도착했어요, 확인해 주세요" : "결과물을 보냈어요, 사장님 확인 중";
    case "completed":
      return "완료된 작업이에요";
    case "notConcluded":
      return "성사되지 않은 작업이에요";
    default:
      return undefined;
  }
}

/** 채팅 목록의 마지막 메시지 글자. 메시지가 없으면 안내 */
export function chatLastMessageText(room: ChatRoom): string {
  const last = room.lastMessage;
  if (!last) return "아직 메시지가 없어요";
  if (last.type === "IMAGE") return "사진";
  if (last.type === "FILE") return `파일 · ${last.preview}`;
  return last.preview;
}

/** 선정된 지원서의 작업계획서. 제안으로 시작한 작업은 없다 (undefined) */
export function chatPlanOf(room: ChatRoom): ApplicationPlan | undefined {
  const { applicationSummary, applicationWorkPlan, applicationDeliveryMethod } = room;
  if (!applicationSummary || !applicationWorkPlan || !applicationDeliveryMethod) return undefined;
  return { summary: applicationSummary, method: applicationWorkPlan, deliverable: applicationDeliveryMethod };
}

/** 사장님 「작업 취소」: 작업 상태가 매칭이고 확인할 결과물이 없을 때만 */
export function canCancelChatWork(room: ChatRoom): boolean {
  return room.jobStatus === "MATCHED" && room.submissionReviewStatus !== "PENDING";
}

/** 「문제 신고」: 작업 상태가 매칭일 때만 */
export function canReportChatWork(room: ChatRoom): boolean {
  return room.jobStatus === "MATCHED";
}

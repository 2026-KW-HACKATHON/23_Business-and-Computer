import { numericTargetId } from "../../notification";
import type { NotificationItem } from "../../notification";
import { OWNER_PATHS } from "./paths";

/**
 * 사장님이 알림을 누르면 가는 화면. 종류에 맞는 화면을 먼저 보고(대상 종류가 맞을 때만), 없으면
 * (모르는 종류 포함) 대상 종류의 기본 화면으로 간다. 모르는 대상이거나 id 가 맞지 않으면 undefined
 * (목록에 보이되 이동하지 않는다).
 * - JOB_APPLICATION_RECEIVED → 지원자 목록
 * - JOB_DRAFT_SUBMITTED · JOB_REVISION_SUBMITTED → 작업 확인
 * - JOB_REVIEW_REQUESTED → 후기 작성 (이미 남겼으면 그 화면이 남긴 후기로 보낸다)
 * - JOB_COMPLETED → 결과물 보기
 * - PROPOSAL_CANCELLED → 내 활동 › 받은 제안 (서버가 취소된 제안을 사장님에게 주지 않는다)
 * - PROPOSAL_RECEIVED · PROPOSAL_LIKE_MILESTONE_REACHED → 받은 제안 상세 (대상 기본)
 * - JOB_STARTED → 채팅방 (대상 기본), PAYMENT_REFUNDED · PAYMENT_COMPLETED → 결제 내역 (대상 기본)
 */
export function notificationPath(item: NotificationItem): string | undefined {
  const id = numericTargetId(item);
  if (item.targetType === "JOB" && id) {
    switch (item.type) {
      case "JOB_APPLICATION_RECEIVED":
        return OWNER_PATHS.requestApplicants(id);
      case "JOB_DRAFT_SUBMITTED":
      case "JOB_REVISION_SUBMITTED":
        return OWNER_PATHS.workCheck(id);
      case "JOB_REVIEW_REQUESTED":
        return OWNER_PATHS.workReview(id);
      case "JOB_COMPLETED":
        return OWNER_PATHS.workResult(id);
    }
  }
  if (item.type === "PROPOSAL_CANCELLED" && item.targetType === "PROPOSAL") {
    return OWNER_PATHS.activity("proposals");
  }
  switch (item.targetType) {
    case "JOB":
      // 의뢰서 상세는 모집 중 · 진행 중 · 끝난 의뢰 모두 연다
      return id ? OWNER_PATHS.request(id) : undefined;
    case "PROPOSAL":
      return id ? OWNER_PATHS.proposal(id) : undefined;
    case "CHAT_ROOM":
      return item.targetId ? OWNER_PATHS.chat(item.targetId) : undefined;
    case "PAYMENT":
      // 결제 하나를 여는 화면이 없어 결제 내역으로
      return OWNER_PATHS.payments;
    default:
      return undefined;
  }
}

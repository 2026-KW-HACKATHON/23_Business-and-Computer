import { numericTargetId } from "../../notification";
import type { NotificationItem } from "../../notification";
import { fetchMyProposals } from "../api/proposalApi";
import { STUDENT_PATHS } from "./paths";

/**
 * 학생이 알림을 누르면 가는 화면. 종류에 맞는 화면을 먼저 보고(대상 종류가 맞을 때만), 없으면
 * (모르는 종류 포함) 대상 종류의 기본 화면으로 간다. 모르는 대상이거나 id 가 맞지 않으면 undefined
 * (목록에 보이되 이동하지 않는다). PROPOSAL_ACCEPTED 는 대상이 제안이 아니라 결제로 만들어진
 * 의뢰라서 resolveNotificationPath 가 보낸 제안에서 찾는다 (여기서는 undefined).
 * - JOB_APPLICATION_REJECTED · JOB_RECRUITMENT_CANCELLED → 의뢰서 (모집이 끝났거나 취소된 의뢰도 연다)
 * - JOB_REVISION_REQUESTED → 수정 요청 확인
 * - JOB_REVIEW_RECEIVED → 받은 후기
 * - JOB_COMPLETED → 내 결과물
 * - PAYMENT_SETTLED → 정산 내역
 * - JOB_APPLICATION_SELECTED · JOB_CANCELLED_BY_OWNER → 채팅방 (대상 기본)
 * - PROPOSAL_REJECTED · PROPOSAL_LIKE_MILESTONE_REACHED → 보낸 제안 상세 (대상 기본)
 */
export function notificationPath(item: NotificationItem): string | undefined {
  const id = numericTargetId(item);
  if (item.targetType === "JOB") {
    switch (item.type) {
      case "PROPOSAL_ACCEPTED":
        return undefined;
      case "PAYMENT_SETTLED":
        return STUDENT_PATHS.settlements;
    }
  }
  if (item.targetType === "JOB" && id) {
    switch (item.type) {
      case "JOB_APPLICATION_REJECTED":
      case "JOB_RECRUITMENT_CANCELLED":
        return STUDENT_PATHS.requestFull(id);
      case "JOB_REVISION_REQUESTED":
        return STUDENT_PATHS.workRevision(id);
      case "JOB_REVIEW_RECEIVED":
        return STUDENT_PATHS.workReview(id);
      case "JOB_COMPLETED":
        return STUDENT_PATHS.workResult(id);
    }
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

/** 보낸 제안에서 의뢰 id 로 찾는 데 쓰는 값 (GET /me/proposals 의 한 줄) */
type ProposalJobRef = { proposalId: number; jobId?: number | null };

/**
 * notificationPath 에 PROPOSAL_ACCEPTED 를 더한 것. 수락된 제안의 의뢰 id(targetId)로 보낸 제안
 * (GET /me/proposals)에서 jobId 가 같은 제안을 찾아 작업 시작 화면으로 보내고, 못 찾거나 불러오지
 * 못하면 내 활동 › 보낸 제안으로 보낸다
 */
export async function resolveNotificationPath(
  item: NotificationItem,
  loadProposals: () => Promise<ProposalJobRef[]> = fetchMyProposals,
): Promise<string | undefined> {
  if (item.type !== "PROPOSAL_ACCEPTED" || item.targetType !== "JOB") return notificationPath(item);
  const jobId = numericTargetId(item);
  try {
    const proposals = jobId ? await loadProposals() : [];
    const proposal = proposals.find((p) => p.jobId !== null && p.jobId !== undefined && String(p.jobId) === jobId);
    return proposal ? STUDENT_PATHS.proposalStart(String(proposal.proposalId)) : STUDENT_PATHS.activity("proposals");
  } catch {
    return STUDENT_PATHS.activity("proposals");
  }
}

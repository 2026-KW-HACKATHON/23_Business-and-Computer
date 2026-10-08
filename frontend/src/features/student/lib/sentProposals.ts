import { ApiError } from "../../../api/client";
import type { FlowStep } from "../../../components";
import { proposalMonthDay } from "../../proposal";
import type { ProposalJobStatus, ProposalStatus } from "../../proposal";
import { cancelMyProposal, fetchMyProposals } from "../api/proposalApi";
import type { MyProposalResponse } from "../api/proposalApi";
import { flowSteps } from "./flow";

/** 보낸 제안 목록의 제안 하나 (GET /me/proposals). 상세는 features/proposal 의 ProposalDetail */
export type SentProposal = MyProposalResponse;

/**
 * 상태 칩 글자. 거절 · 취소된 제안과 의뢰가 취소된 제안(목록 jobStatus · 상세 agreement.jobStatus 가
 * CANCELLED)은 누가 했든 「성사되지 않음」, 제안으로 시작한 일이 끝났으면(jobStatus CLOSED,
 * 제안은 ACCEPTED 그대로) 「완료」.
 */
export function sentProposalStatusLabel(
  status: ProposalStatus,
  jobStatus?: ProposalJobStatus | null,
): string {
  if (jobStatus === "CANCELLED") return "성사되지 않음";
  if (jobStatus === "CLOSED") return "완료";
  switch (status) {
    case "PENDING":
      return "수락 대기 중";
    case "AWAITING_START":
      return "수락됨";
    case "ACCEPTED":
      return "작업 중";
    case "REJECTED":
    case "CANCELLED":
      return "성사되지 않음";
  }
}

/** 작업 중인 제안. 칩을 다른 상태와 다른 색으로 보인다 */
export function sentProposalInProgress(
  status: ProposalStatus,
  jobStatus?: ProposalJobStatus | null,
): boolean {
  return status === "ACCEPTED" && jobStatus !== "CLOSED" && jobStatus !== "CANCELLED";
}

/**
 * 흐름 막대 (제안 → 시작 → 초안 → 수정 → 완료). 수락 대기 = 제안, 수락됨(결제 완료) = 시작,
 * 작업 중 = 초안, 끝난 일 = 모든 단계 완료. 성사되지 않은 제안은 막대를 보이지 않는다 (undefined).
 */
export function sentProposalFlowSteps(
  status: ProposalStatus,
  jobStatus?: ProposalJobStatus | null,
): FlowStep[] | undefined {
  if (jobStatus === "CANCELLED") return undefined;
  if (jobStatus === "CLOSED") return flowSteps("제안", 5);
  switch (status) {
    case "PENDING":
      return flowSteps("제안", 0, "수락 대기");
    case "AWAITING_START":
      return flowSteps("제안", 1, "시작 전");
    case "ACCEPTED":
      return flowSteps("제안", 2, "작업 중");
    case "REJECTED":
    case "CANCELLED":
      return undefined;
  }
}

/** 「10월 5일 보냄」. createdAt 이 없으면 undefined (그 줄을 숨긴다) */
export function sentOnText(
  createdAt: string | null | undefined,
  rejectedAt?: string | null,
): string | undefined {
  const rejectedOn = proposalMonthDay(rejectedAt);
  if (rejectedOn) return `${rejectedOn} 성사되지 않음`;
  const monthDay = proposalMonthDay(createdAt);
  return monthDay && `${monthDay} 보냄`;
}

/** 가게 주소. 비었으면 undefined (주소 줄을 숨긴다) */
export function storeAddressText(address: string | null | undefined): string | undefined {
  return address?.trim() ? address : undefined;
}

/** GET /me/proposals 결과 */
export type SentProposalsResult =
  | { status: "loaded"; proposals: SentProposal[] }
  /** apiData 가 /refresh 로 한 번 다시 시도한 뒤에도 401 */
  | { status: "unauthorized" }
  /** 403 PROPOSAL_403_LIST_STUDENT — 학생(학생 프로필)이 아님 */
  | { status: "forbidden" }
  | { status: "error" };

export async function loadSentProposals(): Promise<SentProposalsResult> {
  try {
    return { status: "loaded", proposals: await fetchMyProposals() };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.code === "PROPOSAL_403_LIST_STUDENT") return { status: "forbidden" };
    }
    return { status: "error" };
  }
}

/** 제안 취소 결과 */
export type ProposalCancelResult =
  | { status: "cancelled" }
  | {
      status:
        | "unauthorized"
        /** 403 PROPOSAL_403_CANCEL — 이 제안을 보낸 학생이 아님 */
        | "forbidden"
        /** 404 PROPOSAL_404 */
        | "notFound"
        /** 409 PROPOSAL_409_CANCEL — 수락 대기가 아님 (이미 수락 · 시작 · 거절) */
        | "notAvailable"
        /** 409 PROPOSAL_409_CANCEL_PAYMENT_PENDING — 사장님이 결제하는 중 */
        | "paymentPending"
        /** 5xx · 네트워크 */
        | "error";
    };

/** 보낸 제안을 취소하고 결과를 화면이 쓰는 값으로 바꾼다 */
export async function sendProposalCancel(proposalId: number): Promise<ProposalCancelResult> {
  try {
    await cancelMyProposal(proposalId);
    return { status: "cancelled" };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.code === "PROPOSAL_403_CANCEL") return { status: "forbidden" };
      if (error.code === "PROPOSAL_404") return { status: "notFound" };
      if (error.code === "PROPOSAL_409_CANCEL") return { status: "notAvailable" };
      if (error.code === "PROPOSAL_409_CANCEL_PAYMENT_PENDING") return { status: "paymentPending" };
    }
    return { status: "error" };
  }
}

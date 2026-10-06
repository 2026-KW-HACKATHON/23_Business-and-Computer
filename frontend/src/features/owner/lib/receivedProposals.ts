import { ApiError } from "../../../api/client";
import type { FlowStep } from "../../../components";
import { proposalMonthDay } from "../../proposal";
import type { ProposalJobStatus, ProposalStatus, ProposalStudentResponse } from "../../proposal";
import { fetchReceivedProposals } from "../api/receivedProposalApi";
import type { ReceivedProposalResponse } from "../api/receivedProposalApi";
import { flowSteps } from "./flow";

/** 받은 제안 목록의 제안 하나 (GET /me/received-proposals). 상세는 features/proposal 의 ProposalDetail */
export type ReceivedProposal = ReceivedProposalResponse;

/**
 * 사장님 쪽 상태 칩 글자. 거절 · 취소된 제안과 의뢰가 취소된 제안(jobStatus CANCELLED)은 누가 했든
 * 「성사되지 않음」. 목록은 서버가 jobStatus 를 줄 때만 의뢰 상태를 본다. 모르는 상태는 「확인 필요」.
 */
export function receivedProposalStatusLabel(
  status: ProposalStatus,
  jobStatus?: ProposalJobStatus | null,
): string {
  if (jobStatus === "CANCELLED") return "성사되지 않음";
  switch (status) {
    case "PENDING":
      return "결정 대기";
    case "AWAITING_START":
      return "결제 완료";
    case "ACCEPTED":
      return "작업 중";
    case "REJECTED":
    case "CANCELLED":
      return "성사되지 않음";
    default:
      return "확인 필요";
  }
}

/**
 * 흐름 막대 (제안 → 시작 → 초안 → 수정 → 완료). 결정 대기 = 제안, 결제 완료 = 시작(학생이 시작 전),
 * 작업 중 = 초안. 성사되지 않은 제안과 모르는 상태는 막대를 보이지 않는다 (undefined).
 */
export function receivedProposalFlowSteps(
  status: ProposalStatus,
  jobStatus?: ProposalJobStatus | null,
): FlowStep[] | undefined {
  if (jobStatus === "CANCELLED") return undefined;
  switch (status) {
    case "PENDING":
      return flowSteps("제안", 0, "결정해 주세요");
    case "AWAITING_START":
      return flowSteps("제안", 1, "학생 시작 전");
    case "ACCEPTED":
      return flowSteps("제안", 2, "작업 중");
    default:
      return undefined;
  }
}

/** 「10월 5일 도착」. createdAt 이 없으면 undefined (그 줄을 숨긴다) */
export function receivedOnText(createdAt: string | null | undefined): string | undefined {
  const monthDay = proposalMonthDay(createdAt);
  return monthDay && `${monthDay} 도착`;
}

/**
 * 학번 → 「24학번」. 목록은 10자리(2024402001)라 앞 네 자리 중 뒤 두 자리, 상세는 이미 두 자리("24")다.
 * 모양이 다르면 undefined.
 */
export function admissionYearText(studentNumber: string | null | undefined): string | undefined {
  const value = studentNumber?.trim() ?? "";
  if (/^\d{10}$/.test(value)) return `${value.slice(2, 4)}학번`;
  if (/^\d{2}$/.test(value)) return `${value}학번`;
  return undefined;
}

/** 학생 줄 「24학번 · 경영학부」. 없는 값은 빼고, 둘 다 없으면 undefined */
export function studentMetaText(
  studentNumber: string | null | undefined,
  major: string | null | undefined,
): string | undefined {
  const parts = [admissionYearText(studentNumber), major?.trim()].filter((part): part is string => !!part);
  return parts.length > 0 ? parts.join(" · ") : undefined;
}

/** 상세 학생 기록 「★ 4.8 · 완료 3건」. 후기나 완료가 없으면 「첫 작업이에요」 */
export function proposalStudentRecord({ averageRating, completedJobCount }: ProposalStudentResponse): string {
  if (completedJobCount === 0 || averageRating === null || averageRating === undefined) return "첫 작업이에요";
  return `★ ${averageRating.toFixed(1)} · 완료 ${completedJobCount}건`;
}

/** GET /me/received-proposals 결과 */
export type ReceivedProposalsResult =
  | { status: "loaded"; proposals: ReceivedProposal[] }
  /** apiData 가 /refresh 로 한 번 다시 시도한 뒤에도 401 */
  | { status: "unauthorized" }
  /** 403 PROPOSAL_403_LIST_OWNER (사장님이 아님) · OWNER_403 (사장님 프로필 없음) */
  | { status: "forbidden" }
  | { status: "error" };

export async function loadReceivedProposals(): Promise<ReceivedProposalsResult> {
  try {
    return { status: "loaded", proposals: await fetchReceivedProposals() };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.code === "PROPOSAL_403_LIST_OWNER" || error.code === "OWNER_403") {
        return { status: "forbidden" };
      }
    }
    return { status: "error" };
  }
}

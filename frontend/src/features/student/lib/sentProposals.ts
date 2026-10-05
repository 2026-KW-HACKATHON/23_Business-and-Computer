import { ApiError } from "../../../api/client";
import type { FlowStep } from "../../../components";
import { formatMonthDay } from "../../../lib/date";
import { fetchMyProposals, fetchProposalDetail } from "../api/proposalApi";
import type {
  MyProposalResponse,
  ProposalDetailResponse,
  ProposalJobStatus,
  ProposalSpecialtyCategory,
  ProposalStatus,
} from "../api/proposalApi";
import { flowSteps } from "./flow";

/** 보낸 제안 목록의 제안 하나 (GET /me/proposals) */
export type SentProposal = MyProposalResponse;
/** 보낸 제안 상세 (GET /proposals/{id}) */
export type SentProposalDetail = ProposalDetailResponse;

/**
 * 상태 칩 글자. 사장님이 의뢰를 취소했으면(목록 jobStatus · 상세 agreement.jobStatus 가 CANCELLED)
 * 상태와 관계없이 「취소됨」.
 */
export function sentProposalStatusLabel(
  status: ProposalStatus,
  jobStatus?: ProposalJobStatus | null,
): string {
  if (jobStatus === "CANCELLED") return "취소됨";
  switch (status) {
    case "PENDING":
      return "수락 대기 중";
    case "AWAITING_START":
      return "수락됨";
    case "ACCEPTED":
      return "작업 중";
    case "REJECTED":
      return "거절됨";
  }
}

/**
 * 흐름 막대 (제안 → 시작 → 초안 → 수정 → 완료). 수락 대기 = 제안, 수락됨(결제 완료) = 시작,
 * 작업 중 = 초안. 취소 · 거절된 제안은 막대를 보이지 않는다 (undefined).
 */
export function sentProposalFlowSteps(
  status: ProposalStatus,
  jobStatus?: ProposalJobStatus | null,
): FlowStep[] | undefined {
  if (jobStatus === "CANCELLED") return undefined;
  switch (status) {
    case "PENDING":
      return flowSteps("제안", 0, "수락 대기");
    case "AWAITING_START":
      return flowSteps("제안", 1, "시작 전");
    case "ACCEPTED":
      return flowSteps("제안", 2, "작업 중");
    case "REJECTED":
      return undefined;
  }
}

/** 뱃지로 보일 대분류 이름 (겹치지 않게). 「기타」는 대분류 「기타」 하나라 뱃지도 하나다 */
export function proposalBadgeNames(categories: ProposalSpecialtyCategory[]): string[] {
  return [...new Set(categories.map((category) => category.name))];
}

/**
 * 한국 시각 "2026-10-05T14:03:11" → 「10월 5일 보냄」. 브라우저 시간대로 밀리지 않게 날짜 글자를
 * 그대로 읽는다. 값이 없거나 날짜 모양이 아니면 undefined (그 줄을 숨긴다)
 */
export function sentOnText(createdAt: string | null | undefined): string | undefined {
  const date = createdAt?.slice(0, 10);
  if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return undefined;
  return `${formatMonthDay(date)} 보냄`;
}

/** PENDING 상세: 「수락하면 10월 7일까지 초안, 10월 9일까지 최종」. 서버가 날짜를 안 주면 undefined */
export function estimatedDeadlineText(detail: SentProposalDetail): string | undefined {
  const { estimatedDraftDeadline: draft, estimatedFinalDeadline: final } = detail;
  if (!draft || !final) return undefined;
  return `수락하면 ${formatMonthDay(draft)}까지 초안, ${formatMonthDay(final)}까지 최종`;
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

/** GET /proposals/{id} 결과 */
export type SentProposalDetailResult =
  | { status: "loaded"; proposal: SentProposalDetail }
  | { status: "unauthorized" }
  /** 404 PROPOSAL_404 */
  | { status: "notFound" }
  | { status: "error" };

export async function loadSentProposalDetail(proposalId: number): Promise<SentProposalDetailResult> {
  try {
    return { status: "loaded", proposal: await fetchProposalDetail(proposalId) };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 404) return { status: "notFound" };
    }
    return { status: "error" };
  }
}

/** 주소의 제안 id ("12") → 12. 양의 정수가 아니면 undefined (요청하지 않고 「없음」으로 보인다) */
export function parseProposalId(value: string | undefined): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

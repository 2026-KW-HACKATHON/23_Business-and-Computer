import { apiData } from "../../../api/client";
import type { ProposalJobStatus, ProposalSpecialtyCategory, ProposalStatus } from "../../proposal";

/** POST /proposals 요청 본문 (ProposalCreateRequest) */
export interface ProposalCreateRequest {
  ownerProfileId: number;
  /** 1개 이상 */
  specialtyIds: number[];
  /** 최대 255자 */
  title: string;
  /** 각 최대 500자 */
  customerProblem: string;
  proposedSolution: string;
  workPlan: string;
  /** 원, 1 이상 */
  proposedFee: number;
  /** 수락일부터 날 수. 0 이상, finalDays ≥ draftDays */
  draftDays: number;
  finalDays: number;
  /** 이 학생이 PROPOSAL 용도로 올린 사진 주소 */
  referenceImageUrls: string[];
}

/** POST /proposals — 학생이 가게(사장님)에 제안을 보낸다. 201, 만든 제안 id 를 돌려준다 */
export async function createProposal(request: ProposalCreateRequest): Promise<number> {
  const data = await apiData<{ proposalId: number } | undefined>("/proposals", {
    method: "POST",
    body: JSON.stringify(request),
  });
  if (data?.proposalId === undefined) throw new Error("Proposal response has no id");
  return data.proposalId;
}

/** GET /me/proposals 의 제안 하나 (MyProposalListResponse.MyProposal) */
export interface MyProposalResponse {
  proposalId: number;
  title: string;
  status: ProposalStatus;
  likeCount: number;
  specialtyCategories: ProposalSpecialtyCategory[];
  proposedSolution: string;
  store: {
    ownerProfileId: number;
    storeName: string;
    storeAddress: string;
    profileImageUrl?: string | null;
  };
  /** 결제로 만들어진 의뢰. 결제 전이면 없음 */
  jobId?: number | null;
  /** 그 의뢰의 상태. 결제 전이면 없음 */
  jobStatus?: ProposalJobStatus | null;
  /** 한국 시각, 오프셋 없음 "2026-10-05T14:03:11.123" */
  createdAt?: string | null;
}

/** GET /me/proposals — 로그인한 학생이 보낸 제안 전부, 최신순 */
export async function fetchMyProposals(): Promise<MyProposalResponse[]> {
  const data = await apiData<{ proposals?: MyProposalResponse[] } | undefined>("/me/proposals");
  return data?.proposals ?? [];
}

/**
 * POST /proposals/{proposalId}/cancel — 결제 전(수락 대기) 제안을 취소한다. 공감도 함께 지워진다.
 * 이미 취소한 제안을 다시 보내도 같은 결과다. 취소한 제안은 목록 · 탐색에서 빠진다.
 */
export async function cancelMyProposal(proposalId: number): Promise<void> {
  await apiData<unknown>(`/proposals/${proposalId}/cancel`, { method: "POST" });
}

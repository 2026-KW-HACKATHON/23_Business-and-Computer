import { apiData } from "../../../api/client";

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

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

/** 제안 상태. REJECTED 는 백엔드 코드에서 아직 쓰지 않는다 */
export type ProposalStatus = "PENDING" | "AWAITING_START" | "ACCEPTED" | "REJECTED";

/** 제안에 묶인 의뢰(job) 상태. 사장님이 취소하면 CANCELLED */
export type ProposalJobStatus = "OPEN" | "AWAITING_START" | "MATCHED" | "CLOSED" | "CANCELLED";

/** 대분류 + 고른 특기 */
export interface ProposalSpecialtyCategory {
  id: number;
  name: string;
  specialties: { id: number; name: string }[];
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

/** GET /proposals/{id} 의 결제로 확정된 작업 조건. 결제 전이거나 당사자가 아니면 없음 */
export interface ProposalAgreementResponse {
  jobStatus: ProposalJobStatus;
  budget: number;
  /** "2026-10-12" */
  draftDeadline: string;
  finalDeadline: string;
  revisionCount: number;
  messageToStudent?: string | null;
  paidAt?: string | null;
  startedAt?: string | null;
}

/** GET /proposals/{id} (ProposalDetailResponse). 학생 정보(student)는 이 화면에서 쓰지 않아 뺐다 */
export interface ProposalDetailResponse {
  proposalId: number;
  title: string;
  storeName: string;
  /** 가게의 지금 프로필 주소. 등록하지 않았으면 없음 */
  storeAddress?: string | null;
  likeCount: number;
  specialtyCategories: ProposalSpecialtyCategory[];
  customerProblem: string;
  proposedSolution: string;
  workPlan: string;
  proposedFee: number;
  draftDays: number;
  finalDays: number;
  referenceImageUrls?: string[] | null;
  /** 한국 시각, 오프셋 없음 "2026-10-05T14:03:11.123" */
  createdAt?: string | null;
  status: ProposalStatus;
  /** PENDING 일 때만. 오늘(한국 날짜) 수락하면 생기는 마감일 "2026-10-12" */
  estimatedDraftDeadline?: string | null;
  estimatedFinalDeadline?: string | null;
  jobId?: number | null;
  agreement?: ProposalAgreementResponse | null;
}

/** GET /me/proposals — 로그인한 학생이 보낸 제안 전부, 최신순 */
export async function fetchMyProposals(): Promise<MyProposalResponse[]> {
  const data = await apiData<{ proposals?: MyProposalResponse[] } | undefined>("/me/proposals");
  return data?.proposals ?? [];
}

/** GET /proposals/{proposalId} — 제안 하나 */
export async function fetchProposalDetail(proposalId: number): Promise<ProposalDetailResponse> {
  const data = await apiData<ProposalDetailResponse | undefined>(`/proposals/${proposalId}`);
  if (!data) throw new Error("Proposal detail response has no data");
  return data;
}

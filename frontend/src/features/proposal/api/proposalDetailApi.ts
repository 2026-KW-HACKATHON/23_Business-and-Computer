import { apiData } from "../../../api/client";

/**
 * 제안 상태. REJECTED = 받은 사장님이 결제 전에 거절했거나 결제 뒤 학생이 의뢰서를 거절함,
 * CANCELLED = 결제 전에 학생이 제안을 취소함
 */
export type ProposalStatus = "PENDING" | "AWAITING_START" | "ACCEPTED" | "REJECTED" | "CANCELLED";

/** REJECTED 제안을 누가 거절했는지. OWNER = 결제 전 사장님, STUDENT = 결제 뒤 학생(의뢰서 거절) */
export type ProposalRejectedBy = "OWNER" | "STUDENT";

/** 제안에 묶인 의뢰(job) 상태. 취소 · 거절되면 CANCELLED */
export type ProposalJobStatus = "OPEN" | "AWAITING_START" | "MATCHED" | "CLOSED" | "CANCELLED";

/** 대분류 + 고른 특기 */
export interface ProposalSpecialtyCategory {
  id: number;
  name: string;
  specialties: { id: number; name: string }[];
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

/** GET /proposals/{id} 의 제안한 학생 */
export interface ProposalStudentResponse {
  studentProfileId: number;
  name: string;
  major?: string | null;
  /** 입학년도 뒤 두 자리 ("24") */
  studentNumber?: string | null;
  /** 후기가 없으면 없음 */
  averageRating?: number | null;
  completedJobCount: number;
}

/** GET /proposals/{id} (ProposalDetailResponse) */
export interface ProposalDetailResponse {
  proposalId: number;
  title: string;
  storeName: string;
  /** 가게의 지금 프로필 주소. 등록하지 않았으면 없음 */
  storeAddress?: string | null;
  likeCount: number;
  /** 내가 공감했는지. 서버가 주면 다른 학생 제안서의 하트가 채워진다 */
  likedByMe?: boolean | null;
  specialtyCategories: ProposalSpecialtyCategory[];
  student: ProposalStudentResponse;
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
  /** REJECTED 일 때만. 거절 주체를 기록하기 전에 거절된 제안은 없음 */
  rejectedBy?: ProposalRejectedBy | null;
  /** REJECTED 일 때만. 한국 시각 "2026-10-06T00:30:00+09:00" */
  rejectedAt?: string | null;
  /** PENDING 일 때만. 오늘(한국 날짜) 수락하면 생기는 마감일 "2026-10-12" */
  estimatedDraftDeadline?: string | null;
  estimatedFinalDeadline?: string | null;
  jobId?: number | null;
  agreement?: ProposalAgreementResponse | null;
}

/** GET /proposals/{proposalId} — 제안 하나 (학생 · 사장님 모두) */
export async function fetchProposalDetail(proposalId: number): Promise<ProposalDetailResponse> {
  const data = await apiData<ProposalDetailResponse | undefined>(`/proposals/${proposalId}`);
  if (!data) throw new Error("Proposal detail response has no data");
  return data;
}

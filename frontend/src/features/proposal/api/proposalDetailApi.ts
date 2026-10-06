import { apiData } from "../../../api/client";

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
  /** 사장님이 제안을 열어 봤는지. 서버가 주면 보낸 제안서의 가게 칸에 보인다 */
  seenByOwner?: boolean | null;
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

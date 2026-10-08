import { apiData } from "../../../api/client";
import type {
  ProposalJobStatus,
  ProposalRejectedBy,
  ProposalSpecialtyCategory,
  ProposalStatus,
} from "../../proposal";

/** GET /me/received-proposals 의 제안 하나 (ReceivedProposalListResponse.ReceivedProposal) */
export interface ReceivedProposalResponse {
  proposalId: number;
  title: string;
  status: ProposalStatus;
  likeCount: number;
  specialtyCategories: ProposalSpecialtyCategory[];
  proposedSolution: string;
  student: {
    studentProfileId: number;
    name: string;
    /** 학생이 올린 프로필 사진. 없으면 없음 */
    profileImageUrl?: string | null;
    /** 10자리 학번 (상세는 입학년도 두 자리) */
    studentNumber?: string | null;
    major?: string | null;
  };
  /** 결제로 만들어진 의뢰. 결제 전이면 없음 */
  jobId?: number | null;
  /** 서버가 주면 「성사되지 않음」 칩에 쓴다 */
  jobStatus?: ProposalJobStatus | null;
  /** REJECTED 일 때만. 누가 거절했는지 */
  rejectedBy?: ProposalRejectedBy | null;
  /** REJECTED 일 때만. 「M월 D일 성사되지 않음」에 쓴다. 한국 시각 "2026-10-06T00:30:00+09:00" */
  rejectedAt?: string | null;
  /** 서버가 주면 「M월 D일 도착」에 쓴다. 한국 시각 (+09:00) */
  createdAt?: string | null;
}

/** GET /me/received-proposals — 로그인한 사장님이 받은 제안 전부, 최신순 (페이지 없음) */
export async function fetchReceivedProposals(): Promise<ReceivedProposalResponse[]> {
  const data = await apiData<{ proposals?: ReceivedProposalResponse[] } | undefined>(
    "/me/received-proposals",
  );
  return data?.proposals ?? [];
}

/**
 * POST /proposals/{proposalId}/reject — 받은 사장님이 결제 전(PENDING) 제안을 거절한다 (본문 없음).
 * 모인 공감은 그대로 남고 더는 공감할 수 없다. 이미 거절한 제안을 다시 보내도 성공한다.
 */
export async function rejectReceivedProposal(proposalId: number): Promise<void> {
  await apiData<unknown>(`/proposals/${proposalId}/reject`, { method: "POST" });
}

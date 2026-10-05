import { apiData } from "../../../api/client";
import type { ProposalJobStatus, ProposalSpecialtyCategory, ProposalStatus } from "../../proposal";

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
    /** 10자리 학번 (상세는 입학년도 두 자리) */
    studentNumber?: string | null;
    major?: string | null;
  };
  /** 결제로 만들어진 의뢰. 결제 전이면 없음 */
  jobId?: number | null;
  /** 서버가 주면 「취소됨」 칩에 쓴다 */
  jobStatus?: ProposalJobStatus | null;
  /** 서버가 주면 「M월 D일 도착」에 쓴다. 한국 시각, 오프셋 없음 */
  createdAt?: string | null;
}

/** GET /me/received-proposals — 로그인한 사장님이 받은 제안 전부, 최신순 (페이지 없음) */
export async function fetchReceivedProposals(): Promise<ReceivedProposalResponse[]> {
  const data = await apiData<{ proposals?: ReceivedProposalResponse[] } | undefined>(
    "/me/received-proposals",
  );
  return data?.proposals ?? [];
}

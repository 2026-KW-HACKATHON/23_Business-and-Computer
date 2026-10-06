import { apiData } from "../../../api/client";

/** POST /jobs/{jobId}/start 의 답 (ProposalJobStartResponse) */
export interface WorkStartResponse {
  jobId: number;
  jobStatus: string;
  proposalStatus: string;
  /** "2026-10-06T12:00:00" (한국 시각) */
  startedAt?: string | null;
  chatRoomId?: string | null;
  /** "2026-10-09" */
  draftDeadline: string;
  finalDeadline: string;
}

/**
 * POST /jobs/{jobId}/start — 수락돼 의뢰서가 온 제안의 작업을 시작한다 (마감 · 페널티 동의는 true 만).
 * 이미 시작한 작업을 다시 보내면 처음 시작한 값을 그대로 돌려준다.
 */
export async function startProposalJob(jobId: number): Promise<WorkStartResponse> {
  const data = await apiData<WorkStartResponse | undefined>(`/jobs/${jobId}/start`, {
    method: "POST",
    body: JSON.stringify({ deadlineAndPenaltyAgreed: true }),
  });
  if (!data) throw new Error("Work start response has no data");
  return data;
}

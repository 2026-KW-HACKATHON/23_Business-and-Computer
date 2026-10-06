import { ApiError } from "../../../api/client";
import type { WorkKind } from "../../../types/workKind";
import { fetchOwnerClosedJobs } from "../api/closedApi";
import type { JobResultResponse, OwnerClosedJobResponse, WorkHistoryType } from "../api/closedApi";
import type { JobSpecialtyCategory } from "../api/jobApi";
import { fetchOwnerPayments } from "../api/paymentHistoryApi";
import type { PaymentHistoryItem } from "../api/paymentHistoryApi";
import { fetchReceivedProposals } from "../api/receivedProposalApi";

/** 끝난 결과. completed = 완료, canceled = 성사되지 않음 (취소 · 거절, 누가 했든) */
export type OwnerClosedOutcome = "completed" | "canceled";

/** 끝난 내 의뢰 하나 (GET /me/jobs?status=CLOSED + 결제 내역 · 받은 제안) */
export interface OwnerClosedJob {
  jobId: number;
  /** 학생 제안에서 시작했으면 proposal, 내 의뢰였으면 request */
  kind: WorkKind;
  title: string;
  specialtyCategories: JobSpecialtyCategory[];
  outcome: OwnerClosedOutcome;
  /** 완료한 날 또는 성사되지 않은 날 */
  closedOn: string;
  /** 맡은 학생. 모집 중에 취소한 의뢰는 없음 */
  studentName?: string;
  /** 결제한 작업비와 돌려받은 금액 (GET /payments). 결제가 없었거나 못 불러오면 없음 */
  paidAmount?: number;
  refundAmount?: number;
}

/** 완료된 작업의 결과물 (GET /jobs/{id}/result) */
export type JobResult = JobResultResponse;

const WORK_HISTORY_TEXT: Record<Exclude<WorkHistoryType, "COMPLETED">, string> = {
  STARTED: "안전결제 · 작업 시작",
  DRAFT_SUBMITTED: "초안 도착",
  REVISION_REQUESTED: "수정 요청",
  REVISION_SUBMITTED: "수정안 도착",
};

/** 결과물 보기의 작업 기록 한 줄. 완료는 사장님이 확인했는지 7일 지나 자동으로 됐는지 */
export function workHistoryText(type: WorkHistoryType, normalCompleted: boolean): string {
  if (type === "COMPLETED") return normalCompleted ? "사장님이 완료 확인" : "7일 지나 자동 완료";
  return WORK_HISTORY_TEXT[type] ?? "";
}

export type OwnerClosedJobsResult =
  | { status: "loaded"; jobs: OwnerClosedJob[] }
  | { status: "unauthorized" }
  | { status: "error" };

/**
 * 끝난 내 의뢰를 불러온다. 목록에 없는 작업비 · 환불 금액은 결제 내역(GET /payments)에서,
 * 제안에서 시작했는지는 받은 제안(GET /me/received-proposals)의 jobId 로 채운다. 그 둘은 실패해도
 * 목록은 그대로 보인다 (금액 줄을 숨기고 의뢰로 본다).
 */
export async function loadOwnerClosedJobs(): Promise<OwnerClosedJobsResult> {
  let closed: OwnerClosedJobResponse[];
  try {
    closed = await fetchOwnerClosedJobs();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "error" };
  }
  if (closed.length === 0) return { status: "loaded", jobs: [] };

  const [payments, proposals] = await Promise.all([
    fetchOwnerPayments()
      .then((history) => history.months.flatMap((month) => month.payments))
      .catch((): PaymentHistoryItem[] => []),
    fetchReceivedProposals().catch(() => []),
  ]);
  const paymentByJob = new Map(payments.map((payment) => [payment.jobId, payment]));
  const proposalJobIds = new Set(proposals.map((proposal) => proposal.jobId));

  return {
    status: "loaded",
    jobs: closed.map((job) => {
      const payment = paymentByJob.get(job.jobId);
      return {
        jobId: job.jobId,
        kind: proposalJobIds.has(job.jobId) ? "proposal" : "request",
        title: job.title,
        specialtyCategories: job.specialtyCategories,
        outcome: job.progressStage === "CANCELLED" ? "canceled" : "completed",
        closedOn: job.completedAt,
        studentName: job.matchedWorker?.name.trim() || undefined,
        paidAmount: payment?.amount,
        refundAmount: payment?.refundAmount ?? undefined,
      };
    }),
  };
}

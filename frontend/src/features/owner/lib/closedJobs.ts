import { ApiError } from "../../../api/client";
import type { WorkKind } from "../../../types/workKind";
import { createJobReview, fetchOwnerClosedJobs } from "../api/closedApi";
import type {
  JobResultResponse,
  OwnerClosedJobResponse,
  ReviewPositivePoint,
  WorkHistoryType,
} from "../api/closedApi";
import type { JobSpecialtyCategory } from "../api/jobApi";
import { fetchOwnerPayments } from "../api/paymentHistoryApi";
import type { PaymentHistoryItem } from "../api/paymentHistoryApi";
import { fetchReceivedProposals } from "../api/receivedProposalApi";
import type { PaymentSummary } from "../types";
import { paymentSummaryOf } from "./paymentHistory";

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
  /** 후기를 남겼는지 (목록의 reviewed) */
  reviewed: boolean;
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
  | {
      status: "loaded";
      jobs: OwnerClosedJob[];
      /** 결제 요약 (GET /payments, 완료 탭 위). 못 불러오면 없음 */
      paymentSummary?: PaymentSummary;
    }
  | { status: "unauthorized" }
  | { status: "error" };

/**
 * 끝난 내 의뢰를 불러온다. 목록에 없는 작업비 · 환불 금액과 결제 요약은 결제 내역(GET /payments,
 * 목록과 함께 보낸다)에서, 제안에서 시작했는지는 받은 제안(GET /me/received-proposals)의 jobId 로
 * 채운다. 그 둘은 실패해도 목록은 그대로 보인다 (금액 줄 · 요약을 숨기고 의뢰로 본다).
 */
export async function loadOwnerClosedJobs(): Promise<OwnerClosedJobsResult> {
  const history = fetchOwnerPayments().catch(() => undefined);
  let closed: OwnerClosedJobResponse[];
  try {
    closed = await fetchOwnerClosedJobs();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "error" };
  }
  const [paid, proposals] = await Promise.all([
    history,
    closed.length > 0 ? fetchReceivedProposals().catch(() => []) : [],
  ]);
  const paymentSummary = paid && paymentSummaryOf(paid);
  if (closed.length === 0) return { status: "loaded", jobs: [], paymentSummary };

  const payments: PaymentHistoryItem[] = paid?.months.flatMap((month) => month.payments) ?? [];
  const paymentByJob = new Map(payments.map((payment) => [payment.jobId, payment]));
  const proposalJobIds = new Set(proposals.map((proposal) => proposal.jobId));

  return {
    status: "loaded",
    paymentSummary,
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
        reviewed: job.reviewed === true,
      };
    }),
  };
}

/** 후기 「좋았던 점」 칩 (피그마 순서)과 서버 값 */
export const REVIEW_POINTS: { label: string; value: ReviewPositivePoint }[] = [
  { label: "결과물이 좋아요", value: "QUALITY_OUTPUT" },
  { label: "마감을 잘 지켜요", value: "ON_TIME_DELIVERY" },
  { label: "소통이 빨라요", value: "FAST_COMMUNICATION" },
  { label: "친절해요", value: "KINDNESS" },
  { label: "수정을 잘 반영해요", value: "REVISION_FEEDBACK" },
];

/** 별점 말 (1 ~ 5점, 0 은 비움) */
export const REVIEW_RATING_LABELS = ["", "별로예요", "아쉬워요", "보통이에요", "좋아요", "최고예요"];

/** 후기 결과 */
export type JobReviewResult =
  | { status: "done" }
  | {
      status:
        | "unauthorized"
        | "forbidden"
        /** 409 REVIEW_409_DUPLICATE — 이미 후기를 남김 */
        | "duplicate"
        /** 404 JOB_404 · 409 REVIEW_409_STATUS — 내 작업이 아니거나 끝나지 않음 */
        | "notAvailable"
        /** 400 — 별점 · 글 확인 */
        | "invalidInput"
        | "error";
    };

/** 후기를 남긴다 (POST /jobs/{id}/reviews). 글은 선택이라 비워 두면 보내지 않는다 */
export async function sendJobReview(
  jobId: number,
  review: { rating: number; pointLabels: string[]; text: string },
): Promise<JobReviewResult> {
  // 고른 순서와 관계없이 칩 순서대로
  const points = REVIEW_POINTS.filter((point) => review.pointLabels.includes(point.label));
  const content = review.text.trim();
  try {
    await createJobReview(jobId, {
      rating: review.rating,
      positivePoints: points.map((point) => point.value),
      ...(content ? { content } : {}),
    });
    return { status: "done" };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
      if (error.code === "REVIEW_409_DUPLICATE") return { status: "duplicate" };
      if (error.status === 404 || error.code === "REVIEW_409_STATUS") return { status: "notAvailable" };
      if (error.status === 400) return { status: "invalidInput" };
    }
    return { status: "error" };
  }
}

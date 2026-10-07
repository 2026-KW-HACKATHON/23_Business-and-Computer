import { ApiError } from "../../../api/client";
import type { WorkKind } from "../../../types/workKind";
import { fetchJobDetail } from "../../explore";
import type { ExploreSpecialtyCategory } from "../../explore";
import { fetchJobResult, fetchReceivedReview } from "../api/finishedApi";
import { fetchMyProposals } from "../api/proposalApi";
import { fetchSettlements } from "../api/settlementApi";

/** 끝난 결과. completed = 완료(정산 완료), canceled = 성사되지 않음 (착수 보상 · 의뢰서 거절) */
export type FinishedOutcome = "completed" | "canceled";

/** 내가 맡았다가 끝난 작업 하나 (GET /settlements 의 정산 예정이 아닌 줄) */
export interface FinishedJob {
  jobId: number;
  /** 내 제안에서 시작했으면 proposal, 의뢰에 지원했으면 request */
  kind: WorkKind;
  title: string;
  storeName?: string;
  outcome: FinishedOutcome;
  /** 완료한 날 또는 성사되지 않은 날 (한국 날짜) */
  closedOn?: string;
  /** 정산된 작업비 · 착수 보상 (의뢰서 거절은 0) */
  amount: number;
  /** 의뢰의 분야 (details 로 불러왔을 때만, 못 불러오면 빈 목록) */
  specialtyCategories: ExploreSpecialtyCategory[];
  /** 받은 후기 별점 (reviews 로 불러왔고 후기가 있을 때만) */
  rating?: number;
  /** 최종 결과물 파일 주소 (files 로 불러왔을 때만) */
  fileUrls?: string[];
}

/** 끝난 작업마다 더 불러올 것. 화면에 필요한 것만 켠다 */
export interface FinishedJobsOptions {
  /** 분야 (GET /jobs/{id}) */
  details?: boolean;
  /** 받은 후기 별점 (GET /jobs/{id}/review, 완료만) */
  reviews?: boolean;
  /** 최종 결과물 파일 (GET /jobs/{id}/result, 완료만) */
  files?: boolean;
}

export type FinishedJobsResult =
  | { status: "loaded"; jobs: FinishedJob[] }
  | { status: "unauthorized" }
  | { status: "error" };

/**
 * 끝난 내 작업 (최근 끝난 것부터). 학생이 맡는 일은 모두 결제된 일이라 정산 내역에 다 있다:
 * 정산 완료 = 완료, 착수 보상 · 환불(의뢰서 거절) = 성사되지 않음. 제안에서 시작했는지는 보낸 제안
 * (GET /me/proposals)의 jobId 로 본다. 작업마다 더 부르는 것(options)은 실패해도 목록은 그대로 보인다.
 */
export async function loadFinishedJobs(options: FinishedJobsOptions = {}): Promise<FinishedJobsResult> {
  const proposals = fetchMyProposals().catch(() => []);
  let rows;
  try {
    const history = await fetchSettlements();
    rows = history.months
      .flatMap((month) => month.settlements)
      .filter((row) => row.status !== "SCHEDULED");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "error" };
  }
  const proposalJobIds = new Set((await proposals).flatMap((p) => (p.jobId ? [p.jobId] : [])));

  const jobs = await Promise.all(
    rows.map(async (row): Promise<FinishedJob> => {
      const completed = row.status === "SETTLED";
      const [detail, review, result] = await Promise.all([
        options.details ? fetchJobDetail(row.jobId).catch(() => undefined) : undefined,
        options.reviews && completed ? fetchReceivedReview(row.jobId).catch(() => undefined) : undefined,
        options.files && completed ? fetchJobResult(row.jobId).catch(() => undefined) : undefined,
      ]);
      return {
        jobId: row.jobId,
        kind: proposalJobIds.has(row.jobId) ? "proposal" : "request",
        title: row.title,
        storeName: row.storeName?.trim() || undefined,
        outcome: completed ? "completed" : "canceled",
        closedOn: row.settledDate ?? undefined,
        amount: row.amount,
        specialtyCategories: detail?.specialtyCategories ?? [],
        rating: review?.rating,
        fileUrls: result?.fileUrls,
      };
    }),
  );
  // 서버는 결제한 달로 묶어 주므로 끝난 날 최신순으로 다시 놓는다
  jobs.sort((a, b) => (b.closedOn ?? "").localeCompare(a.closedOn ?? ""));
  return { status: "loaded", jobs };
}

import { ApiError } from "../../../api/client";
import { fetchJobDetail } from "../../explore";
import { fetchJobResult, fetchReceivedReview } from "../api/finishedApi";
import type {
  JobResultResponse,
  ReceivedReviewResponse,
  ReviewPositivePoint,
  WorkHistoryType,
} from "../api/finishedApi";

/** 받은 후기 (GET /jobs/{id}/review) */
export type ReceivedReview = ReceivedReviewResponse;

/** 내 결과물 화면에 보이는 것: 결과물, 가게 이름, 받은 후기 (가게 이름 · 후기는 없을 수 있음) */
export interface FinishedWork {
  result: JobResultResponse;
  storeName?: string;
  review?: ReceivedReview;
}

export type FinishedLoadResult<T> =
  | { status: "loaded"; data: T }
  | { status: "unauthorized" }
  /** 404 · 403 — 아직 끝나지 않았거나, 후기가 없거나, 내 작업이 아님 */
  | { status: "notFound" }
  | { status: "error" };

function failure(error: unknown): FinishedLoadResult<never> {
  if (error instanceof ApiError) {
    if (error.status === 401) return { status: "unauthorized" };
    if (error.status === 404 || error.status === 403) return { status: "notFound" };
  }
  return { status: "error" };
}

/**
 * 완료된 내 작업의 결과물 (GET /jobs/{id}/result). 가게 이름(GET /jobs/{id})과 받은 후기
 * (GET /jobs/{id}/review)를 함께 보내고, 그 둘은 실패해도 결과물은 그대로 보인다.
 */
export async function loadFinishedWork(jobId: number): Promise<FinishedLoadResult<FinishedWork>> {
  const detail = fetchJobDetail(jobId).catch(() => undefined);
  const review = fetchReceivedReview(jobId).catch(() => undefined);
  try {
    const result = await fetchJobResult(jobId);
    const [job, received] = await Promise.all([detail, review]);
    return {
      status: "loaded",
      data: { result, storeName: job?.storeName?.trim() || undefined, review: received },
    };
  } catch (error) {
    return failure(error);
  }
}

/** 받은 후기를 불러온다 */
export async function loadReceivedReview(jobId: number): Promise<FinishedLoadResult<ReceivedReview>> {
  try {
    return { status: "loaded", data: await fetchReceivedReview(jobId) };
  } catch (error) {
    return failure(error);
  }
}

/** 후기 「좋았던 점」 칩 글자 (사장님 후기 작성의 칩과 같은 말) */
export const REVIEW_POINT_LABEL: Record<ReviewPositivePoint, string> = {
  QUALITY_OUTPUT: "결과물이 좋아요",
  ON_TIME_DELIVERY: "마감을 잘 지켜요",
  FAST_COMMUNICATION: "소통이 빨라요",
  KINDNESS: "친절해요",
  REVISION_FEEDBACK: "수정을 잘 반영해요",
};

const WORK_HISTORY_TEXT: Record<Exclude<WorkHistoryType, "COMPLETED">, string> = {
  STARTED: "작업 시작",
  DRAFT_SUBMITTED: "초안 제출",
  REVISION_REQUESTED: "수정 요청 받음",
  REVISION_SUBMITTED: "수정안 제출",
};

/** 내 결과물의 작업 기록 한 줄. 완료는 사장님이 확인했는지 7일 지나 자동으로 됐는지 */
export function workHistoryText(type: WorkHistoryType, normalCompleted: boolean): string {
  if (type === "COMPLETED") return normalCompleted ? "사장님이 완료 확인" : "7일 지나 자동 완료";
  return WORK_HISTORY_TEXT[type] ?? "";
}

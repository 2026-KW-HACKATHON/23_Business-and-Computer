import { apiData } from "../../../api/client";

/** 작업 기록 한 줄의 종류 */
export type WorkHistoryType =
  | "STARTED"
  | "DRAFT_SUBMITTED"
  | "REVISION_REQUESTED"
  | "REVISION_SUBMITTED"
  | "COMPLETED";

/** GET /jobs/{jobId}/result 의 답. 완료된 작업의 최종 결과물과 작업 기록 (사장님과 맡은 학생만) */
export interface JobResultResponse {
  jobId: number;
  title: string;
  /** 맡은 학생 (나) */
  studentName: string;
  /** 완료한 날 "2026-10-06" */
  completedAt: string;
  /** 사장님이 직접 완료했으면 true, 7일 지나 자동 완료됐으면 false */
  normalCompleted: boolean;
  /** 작업비(원) */
  workFee: number;
  /** 최종 결과물 파일 주소. 끝 경로가 올린 파일 이름 */
  fileUrls: string[];
  /** 최종 결과물과 함께 사장님께 남긴 한마디 */
  message: string;
  /** 오래된 것부터 */
  workHistory: { type: WorkHistoryType; date: string }[];
}

/** GET /jobs/{jobId}/result — 완료된 내 작업의 결과물. 끝나지 않았거나 내 작업이 아니면 404 JOB_RESULT_404 */
export async function fetchJobResult(jobId: number): Promise<JobResultResponse> {
  const data = await apiData<JobResultResponse | undefined>(`/jobs/${jobId}/result`);
  if (!data) throw new Error("Job result response has no data");
  return data;
}

/** 후기의 좋았던 점 */
export type ReviewPositivePoint =
  | "QUALITY_OUTPUT"
  | "ON_TIME_DELIVERY"
  | "FAST_COMMUNICATION"
  | "KINDNESS"
  | "REVISION_FEEDBACK";

/** GET /jobs/{jobId}/review 의 답 (StudentReviewResponse). 사장님이 남긴 후기 */
export interface ReceivedReviewResponse {
  submissionId: number;
  jobTitle: string;
  storeName?: string | null;
  /** 1 ~ 5 */
  rating: number;
  /** 후기를 남긴 날 "2026-10-06" */
  createdAt: string;
  positivePoints: ReviewPositivePoint[];
  content?: string | null;
}

/**
 * GET /jobs/{jobId}/review — 완료된 내 작업에서 받은 후기. 아직 후기가 없거나 내 작업이 아니면
 * 404 REVIEW_404, 학생이 아니면 403
 */
export async function fetchReceivedReview(jobId: number): Promise<ReceivedReviewResponse> {
  const data = await apiData<ReceivedReviewResponse | undefined>(`/jobs/${jobId}/review`);
  if (!data) throw new Error("Received review response has no data");
  return data;
}

import { apiData } from "../../../api/client";
import type { JobSpecialtyCategory } from "./jobApi";

/** GET /me/jobs?status=CLOSED 의 의뢰 하나. 완료됐거나 취소 · 거절돼 끝난 내 의뢰 */
export interface OwnerClosedJobResponse {
  jobId: number;
  title: string;
  specialtyCategories: JobSpecialtyCategory[];
  /** 맡은 학생. 모집 중에 취소한 의뢰는 없음 */
  matchedWorker?: { studentProfileId: number; name: string } | null;
  /** 완료한 날 또는 취소된 날 "2026-10-06" */
  completedAt: string;
  /** COMPLETED = 완료, CANCELLED = 취소 · 거절 */
  progressStage?: string | null;
}

/**
 * GET /me/jobs?status=CLOSED — 끝난 내 의뢰 (끝난 날 최신순). 다른 목록과 달리 data 가
 * { jobs } 가 아니라 배열 그대로다
 */
export async function fetchOwnerClosedJobs(): Promise<OwnerClosedJobResponse[]> {
  const data = await apiData<OwnerClosedJobResponse[] | undefined>("/me/jobs?status=CLOSED");
  return Array.isArray(data) ? data : [];
}

/** 작업 기록 한 줄의 종류 */
export type WorkHistoryType =
  | "STARTED"
  | "DRAFT_SUBMITTED"
  | "REVISION_REQUESTED"
  | "REVISION_SUBMITTED"
  | "COMPLETED";

/** GET /jobs/{jobId}/result 의 답. 완료된 작업의 최종 결과물과 작업 기록 */
export interface JobResultResponse {
  jobId: number;
  title: string;
  studentName: string;
  /** 완료한 날 "2026-10-06" */
  completedAt: string;
  /** 사장님이 직접 완료했으면 true, 7일 지나 자동 완료됐으면 false */
  normalCompleted: boolean;
  /** 작업비(원) */
  workFee: number;
  /** 파일 주소. 끝 경로가 학생이 올린 파일 이름 */
  fileUrls: string[];
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

/** POST /jobs/{jobId}/reviews 본문 */
export interface JobReviewRequest {
  /** 1 ~ 5 */
  rating: number;
  /** 겹치지 않게 5개까지 */
  positivePoints: ReviewPositivePoint[];
  /** 꼭 적어야 함, 5000자까지 */
  content: string;
}

/** POST /jobs/{jobId}/reviews — 완료된 내 작업의 학생에게 후기를 한 번 남긴다 */
export async function createJobReview(jobId: number, request: JobReviewRequest): Promise<void> {
  await apiData<unknown>(`/jobs/${jobId}/reviews`, { method: "POST", body: JSON.stringify(request) });
}

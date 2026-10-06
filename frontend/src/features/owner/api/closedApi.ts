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

/** GET /me/jobs?status=CLOSED — 끝난 내 의뢰 (끝난 날 최신순) */
export async function fetchOwnerClosedJobs(): Promise<OwnerClosedJobResponse[]> {
  const data = await apiData<{ jobs?: OwnerClosedJobResponse[] } | undefined>("/me/jobs?status=CLOSED");
  return data?.jobs ?? [];
}

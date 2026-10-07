import { apiData } from "../../../api/client";
import type { ExploreSpecialtyCategory } from "../../explore";

/** 마지막으로 낸 결과물의 종류 (초안 · 수정안) */
export type SubmissionType = "DRAFT" | "REVISION";

/** 마지막으로 낸 결과물의 검토 상태 */
export type SubmissionReviewStatus = "PENDING" | "APPROVED" | "REVISION_REQUESTED";

/** GET /me/jobs?status=MATCHED (학생) 의 의뢰 하나. 나와 매칭된 진행 중 작업 */
export interface MatchedJobResponse {
  jobId: number;
  title: string;
  specialtyCategories: ExploreSpecialtyCategory[];
  /** 작업비(원) */
  budget: number;
  /** "2026-10-12" */
  draftDeadline: string;
  finalDeadline: string;
  /** 사장님이 정한 수정 횟수 */
  revisionCount: number;
  /** 아직 아무것도 내지 않았으면 null */
  submissionType?: SubmissionType | null;
  reviewStatus?: SubmissionReviewStatus | null;
  progressStage?: string | null;
  /** 의뢰한 사장님의 지금 가게 이름 */
  storeName?: string | null;
  /** 마지막으로 낸 결과물의 제출 시각 (UTC, 오프셋 없음). 아직 아무것도 내지 않았으면 null */
  submittedAt?: string | null;
}

/** GET /me/jobs?status=MATCHED — 나와 매칭된 진행 중 작업 (최신순) */
export async function fetchMatchedJobs(): Promise<MatchedJobResponse[]> {
  const data = await apiData<{ jobs?: MatchedJobResponse[] } | undefined>("/me/jobs?status=MATCHED");
  return data?.jobs ?? [];
}

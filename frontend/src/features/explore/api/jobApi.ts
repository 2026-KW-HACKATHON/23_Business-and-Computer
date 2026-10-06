import { apiData } from "../../../api/client";
import type {
  ExploreSpecialtyCategory,
  JobApplicationStatus,
  JobProgressStage,
  JobStatus,
} from "./exploreApi";

/** GET /jobs/{jobId} 의 의뢰 하나 */
export interface JobDetail {
  id: number;
  title: string;
  /** 맡기고 싶은 일 */
  description: string;
  /** 작업비(원) */
  budget: number;
  specialtyCategories: ExploreSpecialtyCategory[];
  /** "2026-10-12" */
  draftDeadline: string;
  finalDeadline: string;
  revisionCount: number;
  progressStage: JobProgressStage;
  status: JobStatus;
  /** 가게 이름. 서버가 주면 가게 상자가 보인다 */
  storeName?: string | null;
  /** 가게 주소. 서버가 주면 가게 상자에 주소 줄이 보인다 */
  storeAddress?: string | null;
  /** 내 지원서 상태. 지원한 적이 없으면 오지 않고, 오면 「지원했어요」로 바뀐다 */
  applied?: JobApplicationStatus | null;
}

/** GET /jobs/{jobId} — 같은 데모 세션의 의뢰만 (다른 세션은 404 JOB_404) */
export async function fetchJobDetail(jobId: number): Promise<JobDetail> {
  const data = await apiData<JobDetail | undefined>(`/jobs/${jobId}`);
  if (!data) throw new Error("Job detail response has no data");
  return data;
}

/** POST /jobs/{jobId}/applications 본문. 마감 · 페널티 동의는 true 만 받는다 */
export interface JobApplicationCreateRequest {
  /** 255자까지 */
  summary: string;
  /** 500자까지 */
  workPlan: string;
  /** 500자까지 */
  deliveryMethod: string;
  deadlineAndPenaltyAgreed: true;
}

/** POST /jobs/{jobId}/applications — 지원서 id 를 돌려준다 */
export async function createJobApplication(
  jobId: number,
  request: JobApplicationCreateRequest,
): Promise<number> {
  const data = await apiData<{ jobApplicationId: number } | undefined>(`/jobs/${jobId}/applications`, {
    method: "POST",
    body: JSON.stringify(request),
  });
  if (data?.jobApplicationId === undefined) throw new Error("Job application response has no id");
  return data.jobApplicationId;
}

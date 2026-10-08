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
  /** 사장님이 올린 참고 사진 주소. 없으면 빈 목록 */
  referenceImageUrls?: string[] | null;
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
  /** 사장님이 올린 가게 사진. 없으면 없음 */
  storeProfileImageUrl?: string | null;
  /** 내 지원서 상태. 지원한 적이 없으면 오지 않고, 오면 「지원했어요」로 바뀐다 */
  applied?: JobApplicationStatus | null;
  /*
   * 아래 취소 정보는 취소된 의뢰의 사장님 · 맡은 학생에게만 온다. 환불 · 보상 금액은 결제한 의뢰만
   * (모집 중에 취소했으면 없음). 학생이 의뢰서를 거절했으면 cancelledBy 가 STUDENT.
   */
  cancelledBy?: "OWNER" | "STUDENT" | null;
  cancelReason?: string | null;
  messageToStudent?: string | null;
  refundAmount?: number | null;
  studentCompensationAmount?: number | null;
  /** "2026-10-06T12:00:00" */
  cancelledAt?: string | null;
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

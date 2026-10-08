import { apiData } from "../../../api/client";
import type {
  ExploreSpecialtyCategory,
  JobApplicationStatus,
  JobStatus,
} from "../../explore";

/**
 * GET /me/job-applications 의 의뢰 하나. 모집 중인 의뢰에 낸 검토 중 지원서와
 * 다른 학생이 선정돼 선택되지 않은(REJECTED) 지원서. 선정된 지원은 진행 중 목록에 있다
 */
export interface AppliedJobResponse {
  jobId: number;
  jobApplicationId: number;
  title: string;
  specialtyCategories: ExploreSpecialtyCategory[];
  /** 작업비(원) */
  budget: number;
  /** "2026-10-12" */
  draftDeadline: string;
  finalDeadline: string;
  jobStatus: JobStatus;
  applicationStatus: JobApplicationStatus;
  /** 지원한 시각 (한국 시각) "2026-10-06T10:20:30+09:00" */
  appliedAt?: string | null;
  /** 가게 이름. 카드에 「가게, 사장님 검토 중」으로 보인다 */
  storeName?: string | null;
  /** 내가 보낸 한 줄 요약 · 작업계획서 · 결과물. 셋 다 있으면 「내 지원서」에 보인다 */
  summary?: string | null;
  workPlan?: string | null;
  deliveryMethod?: string | null;
}

/** GET /me/job-applications — 최신 지원순. 학생이 아니면 403 JOB_APPLICATION_403_LIST_STUDENT */
export async function fetchMyJobApplications(): Promise<AppliedJobResponse[]> {
  const data = await apiData<{ jobs?: AppliedJobResponse[] } | undefined>("/me/job-applications");
  return data?.jobs ?? [];
}

import { apiData } from "../../../api/client";
import type {
  ExploreSpecialtyCategory,
  JobApplicationStatus,
  JobStatus,
} from "../../explore";

/** GET /me/job-applications 의 의뢰 하나. 모집 중인 의뢰에 낸, 선정을 기다리는 내 지원서 */
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
  /** 지원한 시각 "2026-10-06T10:20:30" */
  appliedAt?: string | null;
  /** 가게 이름. 서버가 주면 카드에 「가게, 사장님 검토 중」으로 보인다 */
  storeName?: string | null;
  /** 내가 보낸 한 줄 요약 · 작업계획서 · 결과물. 서버가 셋 다 주면 「내 지원서」에 보인다 */
  summary?: string | null;
  workPlan?: string | null;
  deliveryMethod?: string | null;
}

/** GET /me/job-applications — 최신 지원순. 학생이 아니면 403 JOB_APPLICATION_403_LIST_STUDENT */
export async function fetchMyJobApplications(): Promise<AppliedJobResponse[]> {
  const data = await apiData<{ jobs?: AppliedJobResponse[] } | undefined>("/me/job-applications");
  return data?.jobs ?? [];
}

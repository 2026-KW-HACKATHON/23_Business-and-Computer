import { apiData } from "../../../api/client";

/** POST /jobs 본문 (JobCreateRequest). 등록한 의뢰는 고칠 수 없다 */
export interface JobCreateRequest {
  /** 고른 할 일의 서버 특기 id (1개 이상, 겹치지 않게) */
  specialtyIds: number[];
  /** 255자까지 */
  title: string;
  description: string;
  /** 작업비(원), 1 이상 */
  budget: number;
  /** "2026-10-12". 오늘 이후, 초안 마감 ≤ 최종 마감 */
  draftDeadline: string;
  finalDeadline: string;
  revisionCount: number;
  /** uploadImage(file, "JOB") 로 받은 주소 (최대 4장, 겹치지 않게) */
  referenceImageUrls: string[];
}

/** POST /jobs — 의뢰 등록. 답에는 데이터가 없다 */
export async function createJob(request: JobCreateRequest): Promise<void> {
  await apiData<unknown>("/jobs", { method: "POST", body: JSON.stringify(request) });
}

/** 대분류 + 그 안의 특기 (목록 · 지원자 응답이 같은 모양) */
export interface JobSpecialtyCategory {
  id: number;
  name: string;
  specialties?: { id: number; name: string }[];
}

/** GET /me/jobs?status=OPEN 의 의뢰 하나 (모집 중인 내 의뢰) */
export interface OpenJobResponse {
  jobId: number;
  title: string;
  specialtyCategories: JobSpecialtyCategory[];
  /** "2026-10-12" */
  draftDeadline: string;
  finalDeadline: string;
  revisionCount: number;
  applicantCount: number;
  progressStage?: string | null;
}

/** GET /me/jobs?status=OPEN — 모집 중인 내 의뢰 */
export async function fetchOpenJobs(): Promise<OpenJobResponse[]> {
  const data = await apiData<{ jobs?: OpenJobResponse[] } | undefined>("/me/jobs?status=OPEN");
  return data?.jobs ?? [];
}

/** 지원자 정렬: 최신 지원순 · 별점 높은 순 · 완료 많은 순 */
export type JobApplicationSort = "LATEST" | "RATING" | "COMPLETED";

/** GET /jobs/{id}/applications 의 지원자 하나 */
export interface JobApplicantResponse {
  jobApplicationId: number;
  studentProfileId: number;
  profileImageUrl?: string | null;
  name: string;
  /** 입학년도 뒤 두 자리 ("24") */
  studentNumber?: string | null;
  major?: string | null;
  /** 후기가 없으면 없음 */
  averageRating?: number | null;
  completedJobCount: number;
  specialtyCategories: JobSpecialtyCategory[];
  summary: string;
  workPlan: string;
  deliveryMethod: string;
}

/** GET /jobs/{id}/applications — 내 모집 중 의뢰의 대기 중 지원자 */
export interface JobApplicationsResponse {
  job: {
    jobId: number;
    title: string;
    specialtyCategories: JobSpecialtyCategory[];
    budget: number;
    draftDeadline: string;
    finalDeadline: string;
  };
  applicantCount: number;
  applicants: JobApplicantResponse[];
}

export async function fetchJobApplications(
  jobId: number,
  sort: JobApplicationSort,
): Promise<JobApplicationsResponse> {
  const data = await apiData<JobApplicationsResponse | undefined>(
    `/jobs/${jobId}/applications?sort=${sort}`,
  );
  if (!data) throw new Error("Job applications response has no data");
  return data;
}

/** GET /jobs/{id}/applications/{applicationId}/profile — 지원자(또는 선정 학생) 프로필 */
export interface ApplicantProfileResponse {
  student: {
    studentProfileId: number;
    name: string;
    university?: string | null;
    major?: string | null;
    studentNumber?: string | null;
  };
  proposalCount: number;
  completedJobCount: number;
  specialtyCategories: JobSpecialtyCategory[];
  /** 한 줄 소개. 서버가 주면 프로필 이름 아래에 보인다 */
  intro?: string | null;
  certificates: { certificateName: string; acquiredYear?: number | null }[];
  portfolioUrl?: string | null;
  penaltyCount: number;
  reviewCount: number;
  /** 글 없는 후기는 content 가 null */
  reviews: { storeName: string; jobTitle: string; content?: string | null; rating: number; createdAt: string }[];
}

export async function fetchApplicantProfile(
  jobId: number,
  applicationId: number,
): Promise<ApplicantProfileResponse> {
  const data = await apiData<ApplicantProfileResponse | undefined>(
    `/jobs/${jobId}/applications/${applicationId}/profile`,
  );
  if (!data) throw new Error("Applicant profile response has no data");
  return data;
}

/**
 * GET /students/{studentProfileId}/profile — 사장님이 보는 학생 프로필 (지원자 프로필과 같은 모양).
 * 의뢰 · 지원 · 제안과 상관없이 볼 수 있다. 사장님이 아니면 403 STUDENT_PROFILE_403_OWNER, 없으면 404 STUDENT_PROFILE_404
 */
export async function fetchStudentProfile(studentProfileId: number): Promise<ApplicantProfileResponse> {
  const data = await apiData<ApplicantProfileResponse | undefined>(`/students/${studentProfileId}/profile`);
  if (!data) throw new Error("Student profile response has no data");
  return data;
}

/** POST /jobs/{id}/cancel 의 답 (JobCancelResponse). 금액은 원, 모집 중 취소면 모두 0 */
export interface JobCancelResponse {
  jobId: number;
  paidAmount?: number | null;
  studentCompensationAmount?: number | null;
  refundAmount?: number | null;
}

/**
 * POST /jobs/{id}/cancel — 모집 중이면 환불 없이 취소, 지원자에게 남길 말이 간다.
 * 진행 중이면 학생 착수 보상(20%)을 뺀 작업비를 돌려받는다.
 */
export async function cancelJob(
  jobId: number,
  request: { cancelReason: string; messageToStudent: string },
): Promise<JobCancelResponse | undefined> {
  return apiData<JobCancelResponse | undefined>(`/jobs/${jobId}/cancel`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

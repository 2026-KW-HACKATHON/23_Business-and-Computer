import { ApiError } from "../../../api/client";
import type { ApplicationPlan } from "../../../types/workPlan";
import {
  cancelJob,
  fetchApplicantProfile,
  fetchJobApplications,
  fetchOpenJobs,
  fetchStudentProfile,
} from "../api/jobApi";
import { fetchJobResult } from "../api/closedApi";
import { fetchLatestJobSubmission, fetchPendingSubmission } from "../api/progressApi";
import type { LatestJobSubmissionResponse } from "../api/progressApi";
import type {
  ApplicantProfileResponse,
  JobApplicantResponse,
  JobApplicationSort,
  JobApplicationsResponse,
  JobSpecialtyCategory,
  OpenJobResponse,
} from "../api/jobApi";

export type OpenJob = OpenJobResponse;
export type JobApplicant = JobApplicantResponse;
export type JobApplications = JobApplicationsResponse;
export type ApplicantProfile = ApplicantProfileResponse;
export type { JobApplicationSort };

/** 사장님 의뢰 API 를 불러온 결과 (화면이 고른다) */
export type OwnerJobResult<T> =
  | { status: "loaded"; data: T }
  | { status: "unauthorized" }
  /** 403 — 사장님이 아니거나 내 의뢰가 아님 */
  | { status: "forbidden" }
  | { status: "notFound" }
  /** 409 — 모집이 끝났거나 취소돼 볼 수 없음 */
  | { status: "closed" }
  | { status: "error" };

async function attempt<T>(run: () => Promise<T>): Promise<OwnerJobResult<T>> {
  try {
    return { status: "loaded", data: await run() };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
      if (error.status === 404) return { status: "notFound" };
      if (error.status === 409) return { status: "closed" };
    }
    return { status: "error" };
  }
}

export const loadOpenJobs = () => attempt(fetchOpenJobs);
export const loadJobApplications = (jobId: number, sort: JobApplicationSort) =>
  attempt(() => fetchJobApplications(jobId, sort));
export const loadApplicantProfile = (jobId: number, applicationId: number) =>
  attempt(() => fetchApplicantProfile(jobId, applicationId));
export const loadStudentProfile = (studentProfileId: number) =>
  attempt(() => fetchStudentProfile(studentProfileId));
export const loadPendingSubmission = (jobId: number) => attempt(() => fetchPendingSubmission(jobId));
export const loadJobResult = (jobId: number) => attempt(() => fetchJobResult(jobId));
/** 서버가 아직 사장님에게 열어 두지 않아 오는 403 은 「아직 없음」으로 본다 (백엔드 요청 중, ADR 0045) */
export const loadLatestJobSubmission = async (jobId: number): Promise<OwnerJobResult<LatestJobSubmissionResponse>> => {
  const result = await attempt(() => fetchLatestJobSubmission(jobId));
  return result.status === "forbidden" ? { status: "notFound" } : result;
};

/** 주소의 id 가 양의 정수인지. 아니면 undefined (요청하지 않는다) */
export function parsePositiveId(id: string | undefined): number | undefined {
  if (!id || !/^[1-9][0-9]*$/.test(id)) return undefined;
  return Number(id);
}

/** 겹치지 않는 대분류 이름 (뱃지) */
export function jobCategoryNames(categories: JobSpecialtyCategory[]): string[] {
  return [...new Set(categories.map((category) => category.name))];
}

/** 고른 특기 이름 (지원자 칩 · 프로필 전공역량) */
export function jobSpecialtyNames(categories: JobSpecialtyCategory[]): string[] {
  return [
    ...new Set(categories.flatMap((category) => (category.specialties ?? []).map((s) => s.name))),
  ];
}

/** 지원서 세 칸을 작업계획서 모양으로 */
export function applicantPlan(applicant: JobApplicant): ApplicationPlan {
  return { summary: applicant.summary, method: applicant.workPlan, deliverable: applicant.deliveryMethod };
}

/** 후기 별점 평균. 후기가 없으면 undefined */
export function averageReviewRating(profile: ApplicantProfile): number | undefined {
  if (profile.reviews.length === 0) return undefined;
  return profile.reviews.reduce((sum, review) => sum + review.rating, 0) / profile.reviews.length;
}

/** 의뢰 취소 결과. 진행 중 작업이면 돌려받는 금액과 학생 착수 보상 (서버가 주면) */
export type JobCancelResult =
  | { status: "canceled"; refundAmount?: number; studentCompensationAmount?: number }
  | {
      status:
        | "unauthorized"
        | "forbidden"
        | "notFound"
        /** 409 JOB_409_CANCEL — 취소할 수 없는 상태 */
        | "notAvailable"
        /** 400 — 이유 · 남길 말 확인 */
        | "invalidInput"
        | "error";
    };

export async function sendJobCancel(
  jobId: number,
  cancelReason: string,
  messageToStudent: string,
): Promise<JobCancelResult> {
  try {
    const data = await cancelJob(jobId, {
      cancelReason: cancelReason.trim(),
      messageToStudent: messageToStudent.trim(),
    });
    return {
      status: "canceled",
      refundAmount: data?.refundAmount ?? undefined,
      studentCompensationAmount: data?.studentCompensationAmount ?? undefined,
    };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
      if (error.status === 404) return { status: "notFound" };
      if (error.status === 409) return { status: "notAvailable" };
      if (error.status === 400) return { status: "invalidInput" };
    }
    return { status: "error" };
  }
}

import { ApiError } from "../../../api/client";
import type { ApplicationPlan } from "../../../types/workPlan";
import { createJobApplication, fetchJobDetail } from "../api/jobApi";
import type { JobApplicationCreateRequest, JobDetail } from "../api/jobApi";

/** 지원서 칸마다 서버가 받는 최대 글자 수 */
export const JOB_APPLICATION_MAX_LENGTH: Record<keyof ApplicationPlan, number> = {
  summary: 255,
  method: 500,
  deliverable: 500,
};

/** 주소의 의뢰 id ("12") → 12. 양의 정수가 아니면 undefined (요청하지 않고 「없음」으로 보인다) */
export function parseJobId(value: string | undefined): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

/** 할 일 칩으로 보일 특기 이름 (겹치지 않게) */
export function jobTaskNames(job: JobDetail): string[] {
  return [
    ...new Set(job.specialtyCategories.flatMap((category) => category.specialties.map((s) => s.name))),
  ];
}

/** GET /jobs/{id} 결과 */
export type JobDetailResult =
  | { status: "loaded"; job: JobDetail }
  /** apiData 가 /refresh 로 한 번 다시 시도한 뒤에도 401 */
  | { status: "unauthorized" }
  /** 404 JOB_404 (없는 의뢰 · 다른 데모 세션의 의뢰) */
  | { status: "notFound" }
  | { status: "error" };

export async function loadJobDetail(jobId: number): Promise<JobDetailResult> {
  try {
    return { status: "loaded", job: await fetchJobDetail(jobId) };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 404) return { status: "notFound" };
    }
    return { status: "error" };
  }
}

/** 화면에서 적은 지원서 → 서버 본문 (앞뒤 공백은 뺀다) */
function toJobApplicationRequest(plan: ApplicationPlan): JobApplicationCreateRequest {
  return {
    summary: plan.summary.trim(),
    workPlan: plan.method.trim(),
    deliveryMethod: plan.deliverable.trim(),
    deadlineAndPenaltyAgreed: true,
  };
}

/** POST /jobs/{id}/applications 결과 */
export type JobApplicationSendResult =
  | { status: "sent" }
  | { status: "unauthorized" }
  /** 403 JOB_APPLICATION_403_STUDENT — 학생(학생 프로필)이 아님 */
  | { status: "notStudent" }
  /** 404 JOB_404 */
  | { status: "notFound" }
  /** 409 JOB_APPLICATION_409_DUPLICATE */
  | { status: "duplicate" }
  /** 409 JOB_APPLICATION_409_STATUS — 모집 중(OPEN)이 아님 */
  | { status: "closed" }
  /** 400 — 빈 칸 · 글자 수 초과 · 동의 없음 */
  | { status: "invalidInput" }
  | { status: "error" };

/** 지원서를 보내고 결과를 화면이 쓰는 값으로 바꾼다 */
export async function sendJobApplication(
  jobId: number,
  plan: ApplicationPlan,
): Promise<JobApplicationSendResult> {
  try {
    await createJobApplication(jobId, toJobApplicationRequest(plan));
    return { status: "sent" };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      switch (error.code) {
        case "JOB_APPLICATION_403_STUDENT":
          return { status: "notStudent" };
        case "JOB_APPLICATION_409_DUPLICATE":
          return { status: "duplicate" };
        case "JOB_APPLICATION_409_STATUS":
          return { status: "closed" };
      }
      if (error.status === 404) return { status: "notFound" };
      if (error.status === 400) return { status: "invalidInput" };
    }
    return { status: "error" };
  }
}

import { ApiError } from "../../../api/client";
import type { ApplicationPlan } from "../../../types/workPlan";
import type { JobApplicationStatus } from "../../explore";
import { proposalMonthDay } from "../../proposal";
import { fetchMyJobApplications } from "../api/applicationApi";
import type { AppliedJobResponse } from "../api/applicationApi";

/** 지원한 의뢰 하나 (GET /me/job-applications) */
export type AppliedJob = AppliedJobResponse;

export type AppliedJobsResult =
  | { status: "loaded"; jobs: AppliedJob[] }
  | { status: "unauthorized" }
  | { status: "forbidden" }
  | { status: "error" };

/** 지원한 의뢰 목록을 불러와 화면에서 고를 수 있는 결과로 바꾼다 */
export async function loadAppliedJobs(): Promise<AppliedJobsResult> {
  try {
    return { status: "loaded", jobs: await fetchMyJobApplications() };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.code === "JOB_APPLICATION_403_LIST_STUDENT") return { status: "forbidden" };
    }
    return { status: "error" };
  }
}

/** 카드의 지원 상태 글자 */
export function appliedStatusLabel(status: JobApplicationStatus): string {
  switch (status) {
    case "PENDING":
      return "사장님 검토 중";
    case "ACCEPTED":
      return "선택됐어요";
    case "REJECTED":
      return "선택되지 않았어요";
  }
}

/** 내가 보낸 지원서. 서버가 세 칸을 다 줄 때만 (하나라도 없으면 undefined) */
export function appliedPlan(job: AppliedJob): ApplicationPlan | undefined {
  if (!job.summary || !job.workPlan || !job.deliveryMethod) return undefined;
  return { summary: job.summary, method: job.workPlan, deliverable: job.deliveryMethod };
}

/** 「10월 6일 지원할 때 보냄」. 지원 시각이 없으면 undefined */
export function appliedOnText(job: AppliedJob): string | undefined {
  const monthDay = proposalMonthDay(job.appliedAt);
  return monthDay && `${monthDay} 지원할 때 보냄`;
}

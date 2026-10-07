import type { JobApplicant, JobApplications } from "../lib/ownerJobs";
import { useJobApplications, useOpenJobs } from "./useOwnerJobs";
import type { OwnerJobLoad } from "./useOwnerJobs";

/** 「이 학생에게 맡기기」 · 안전결제가 보여 주는 의뢰와 고른 지원자 */
export interface JobAssignment {
  job: JobApplications["job"];
  applicant: JobApplicant;
  /** 수정 횟수 (GET /me/jobs?status=OPEN). 못 불러오면 없다 (그 줄을 뺀다) */
  revisionCount: number | undefined;
}

/**
 * 내 모집 중 의뢰의 지원자 하나 (GET /jobs/{id}/applications 에서 applicationId 로 고른다).
 * 목록에 없는 지원서는 notFound, 모집이 끝난 의뢰는 closed. 수정 횟수는 모집 중인 내 의뢰
 * 목록에서 채우고, 그 목록이 실패해도 화면은 보인다.
 */
export function useJobAssignment(
  jobId: number | undefined,
  applicationId: number | undefined,
): { load: OwnerJobLoad<JobAssignment>; reload: () => void } {
  const { load: applications, reload } = useJobApplications(jobId, "LATEST");
  const { load: openJobs } = useOpenJobs();

  if (applications.status !== "loaded") return { load: applications, reload };
  const applicant = applications.data.applicants.find((a) => a.jobApplicationId === applicationId);
  if (!applicant) return { load: { status: "notFound" }, reload };
  // 수정 횟수를 기다리는 동안에는 불러오는 중으로 둔다 (실패하면 그 줄만 뺀다)
  if (openJobs.status === "loading") return { load: { status: "loading" }, reload };
  const revisionCount =
    openJobs.status === "loaded"
      ? openJobs.data.find((job) => job.jobId === jobId)?.revisionCount
      : undefined;
  return { load: { status: "loaded", data: { job: applications.data.job, applicant, revisionCount } }, reload };
}

import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { landingPath } from "../../auth";
import {
  loadApplicantProfile,
  loadJobApplications,
  loadJobResult,
  loadJobSubmissions,
  loadOwnerJobReview,
  loadOpenJobs,
  loadPendingSubmission,
  loadStudentProfile,
} from "../lib/ownerJobs";
import type {
  ApplicantProfile,
  JobApplicationSort,
  JobApplications,
  JobSubmission,
  OpenJob,
  OwnerJobResult,
  OwnerJobReview,
} from "../lib/ownerJobs";
import type { JobResult } from "../lib/closedJobs";
import type { PendingSubmission } from "../lib/submissionReview";

export type OwnerJobLoad<T> =
  | { status: "loading" }
  | { status: "error" }
  | { status: "notFound" }
  | { status: "closed" }
  | { status: "loaded"; data: T };

/**
 * 사장님 의뢰 API 하나를 불러온다. key 가 undefined 면 요청하지 않고 notFound (주소의 id 가 틀림).
 * key 나 다시 시도가 바뀌면 응답이 올 때까지 loading 이고, 지난 응답은 버린다.
 * 401 은 /login, 403 은 알림 뒤 landingPath() 로 보낸다.
 */
function useOwnerJobLoad<T>(
  key: string | undefined,
  run: () => Promise<OwnerJobResult<T>>,
  forbiddenText: string,
): { load: OwnerJobLoad<T>; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ key: string; load: OwnerJobLoad<T> }>();
  const runRef = useRef(run);
  useEffect(() => {
    runRef.current = run;
  });
  const current = key === undefined ? undefined : `${key}:${request}`;

  useEffect(() => {
    if (current === undefined) return;
    let active = true;
    void runRef.current().then((loaded) => {
      if (!active) return;
      if (loaded.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (loaded.status === "forbidden") {
        window.alert(forbiddenText);
        navigate(landingPath(), { replace: true });
      } else {
        setResult({ key: current, load: loaded.status === "loaded" ? loaded : { status: loaded.status } });
      }
    });
    return () => {
      active = false;
    };
  }, [current, forbiddenText, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);
  const load: OwnerJobLoad<T> =
    key === undefined
      ? { status: "notFound" }
      : result !== undefined && result.key === current
        ? result.load
        : { status: "loading" };
  return { load, reload };
}

/** 모집 중인 내 의뢰 (GET /me/jobs?status=OPEN). 내 활동 · 내 정보 · 학생 고르기가 함께 쓴다 */
export function useOpenJobs(): { load: OwnerJobLoad<OpenJob[]>; reload: () => void } {
  return useOwnerJobLoad("open", loadOpenJobs, "사장님만 보낸 의뢰를 볼 수 있어요");
}

/** 내 모집 중 의뢰의 지원자 (GET /jobs/{id}/applications). 모집이 끝났으면 closed */
export function useJobApplications(
  jobId: number | undefined,
  sort: JobApplicationSort,
): { load: OwnerJobLoad<JobApplications>; reload: () => void } {
  return useOwnerJobLoad(
    jobId === undefined ? undefined : `${jobId}:${sort}`,
    () => loadJobApplications(jobId ?? 0, sort),
    "내 의뢰의 지원자만 볼 수 있어요",
  );
}

/** 지원자 프로필 (GET /jobs/{id}/applications/{applicationId}/profile) */
export function useApplicantProfile(
  jobId: number | undefined,
  applicationId: number | undefined,
): { load: OwnerJobLoad<ApplicantProfile>; reload: () => void } {
  return useOwnerJobLoad(
    jobId === undefined || applicationId === undefined ? undefined : `${jobId}:${applicationId}`,
    () => loadApplicantProfile(jobId ?? 0, applicationId ?? 0),
    "내 의뢰의 지원자만 볼 수 있어요",
  );
}

/** 학생 프로필 (GET /students/{id}/profile). 진행 중 작업 · 받은 제안의 「프로필 보기」 */
export function useOwnerStudentProfile(
  studentProfileId: number | undefined,
): { load: OwnerJobLoad<ApplicantProfile>; reload: () => void } {
  return useOwnerJobLoad(
    studentProfileId === undefined ? undefined : String(studentProfileId),
    () => loadStudentProfile(studentProfileId ?? 0),
    "사장님만 학생 프로필을 볼 수 있어요",
  );
}

/** 내 의뢰에 도착한 초안 · 수정안 (GET /jobs/{id}/submission). 확인할 결과물이 없으면 notFound */
export function usePendingSubmission(
  jobId: number | undefined,
): { load: OwnerJobLoad<PendingSubmission>; reload: () => void } {
  return useOwnerJobLoad(
    jobId === undefined ? undefined : String(jobId),
    () => loadPendingSubmission(jobId ?? 0),
    "내 의뢰의 결과물만 볼 수 있어요",
  );
}

/**
 * 내 의뢰의 모든 초안 · 수정안과 각 수정 요청 (GET /jobs/{id}/submissions). 작업 이력 · 지난 초안 ·
 * 보낸 수정 요청이 쓴다
 */
export function useJobSubmissions(
  jobId: number | undefined,
): { load: OwnerJobLoad<JobSubmission[]>; reload: () => void } {
  return useOwnerJobLoad(
    jobId === undefined ? undefined : String(jobId),
    () => loadJobSubmissions(jobId ?? 0),
    "내 의뢰의 결과물만 볼 수 있어요",
  );
}

/** 완료된 내 작업의 결과물 (GET /jobs/{id}/result). 끝나지 않았으면 notFound */
export function useJobResult(jobId: number | undefined): { load: OwnerJobLoad<JobResult>; reload: () => void } {
  return useOwnerJobLoad(
    jobId === undefined ? undefined : String(jobId),
    () => loadJobResult(jobId ?? 0),
    "내 의뢰의 결과물만 볼 수 있어요",
  );
}

/** 완료된 내 작업에 내가 남긴 후기 (GET /jobs/{id}/review). 남기지 않았으면 notFound */
export function useOwnerJobReview(jobId: number | undefined): { load: OwnerJobLoad<OwnerJobReview>; reload: () => void } {
  return useOwnerJobLoad(
    jobId === undefined ? undefined : String(jobId),
    () => loadOwnerJobReview(jobId ?? 0),
    "내 작업의 후기만 볼 수 있어요",
  );
}

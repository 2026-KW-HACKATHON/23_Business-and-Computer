import { ApiError } from "../../../api/client";
import { completeSubmission } from "../api/progressApi";
import type { PendingSubmissionResponse } from "../api/progressApi";

/** 도착해 사장님 확인을 기다리는 초안 · 수정안 (GET /jobs/{id}/submission) */
export type PendingSubmission = PendingSubmissionResponse;

/** 결과물 파일 주소의 끝 경로가 학생이 올린 파일 이름이다 */
export function submissionFileName(url: string): string {
  const last = url.split("?")[0].split("/").pop() ?? "";
  try {
    return decodeURIComponent(last) || "파일";
  } catch {
    return last || "파일";
  }
}

/** 완료 확인 · 수정 요청 결과 */
export type SubmissionReviewResult =
  | { status: "done" }
  | {
      status:
        | "unauthorized"
        | "forbidden"
        /** 404 JOB_404 · JOB_SUBMISSION_404 — 내 의뢰가 아니거나 결과물이 없음 */
        | "notFound"
        /** 409 JOB_SUBMISSION_409_REVIEW_STATUS — 진행 중이 아님 (완료 · 취소) */
        | "notAvailable"
        /** 409 JOB_SUBMISSION_409_REVIEWED — 이미 수정 요청했거나 완료함 */
        | "alreadyReviewed"
        /** 409 JOB_SUBMISSION_409_REVISION_LIMIT — 수정 횟수를 다 씀 */
        | "limitReached"
        /** 5xx · 네트워크 */
        | "error";
    };

async function review(run: () => Promise<void>): Promise<SubmissionReviewResult> {
  try {
    await run();
    return { status: "done" };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
      if (error.status === 404) return { status: "notFound" };
      if (error.code === "JOB_SUBMISSION_409_REVIEW_STATUS") return { status: "notAvailable" };
      if (error.code === "JOB_SUBMISSION_409_REVIEWED") return { status: "alreadyReviewed" };
      if (error.code === "JOB_SUBMISSION_409_REVISION_LIMIT") return { status: "limitReached" };
    }
    return { status: "error" };
  }
}

/** 도착한 결과물을 최종으로 받고 작업을 끝낸다 (POST /jobs/{id}/submissions/{submissionId}/complete) */
export const sendSubmissionComplete = (jobId: number, submissionId: number) =>
  review(() => completeSubmission(jobId, submissionId));

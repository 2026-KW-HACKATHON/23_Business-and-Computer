import { ApiError } from "../../../api/client";
import { completeSubmission, requestSubmissionRevision } from "../api/progressApi";
import type { PendingSubmissionResponse } from "../api/progressApi";
import { uploadRequestPhoto } from "./newRequest";

/** 도착해 사장님 확인을 기다리는 초안 · 수정안 (GET /jobs/{id}/submission) */
export type PendingSubmission = PendingSubmissionResponse;

/** 결과물 파일 주소의 끝 경로가 학생이 올린 파일 이름이다 */
export { fileNameFromUrl as submissionFileName } from "../../../lib/fileUrl";

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
        /** 400 — 적은 내용 확인 */
        | "invalidInput"
        /** 참고 사진을 올리지 못함 (JOB_400_IMAGE_URL · JOB_409_IMAGE_NOT_UPLOADED 포함) */
        | "photoFailed"
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
      if (error.code === "JOB_400_IMAGE_URL" || error.code === "JOB_409_IMAGE_NOT_UPLOADED") {
        return { status: "photoFailed" };
      }
      if (error.status === 400) return { status: "invalidInput" };
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

/**
 * 도착한 결과물에 수정을 요청한다. 참고 사진을 먼저 올리고(JOB 이미지) 적은 내용과 함께
 * POST /jobs/{id}/submissions/{submissionId}/revision-request 로 보낸다
 */
export async function sendRevisionRequest(
  jobId: number,
  submissionId: number,
  message: string,
  photos: File[],
): Promise<SubmissionReviewResult> {
  const referenceImageUrls: string[] = [];
  for (const photo of photos) {
    const uploaded = await uploadRequestPhoto(photo);
    if (uploaded.status === "unauthorized") return { status: "unauthorized" };
    if (uploaded.status !== "uploaded") return { status: "photoFailed" };
    referenceImageUrls.push(uploaded.imageUrl);
  }
  return review(() =>
    requestSubmissionRevision(jobId, submissionId, { message: message.trim(), referenceImageUrls }),
  );
}

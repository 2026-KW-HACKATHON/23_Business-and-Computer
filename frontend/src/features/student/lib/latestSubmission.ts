import { ApiError } from "../../../api/client";
import { koreaDateOfUtc } from "../../../lib/date";
import { fetchLatestSubmission } from "../api/submissionApi";
import type { LatestSubmissionResponse } from "../api/submissionApi";

/** 내가 마지막으로 낸 초안 · 수정안과 거기에 받은 수정 요청 (GET /jobs/{id}/submissions/latest) */
export type LatestSubmission = LatestSubmissionResponse;

export type LatestSubmissionResult =
  | { status: "loaded"; submission: LatestSubmission }
  | { status: "unauthorized" }
  /** 404 — 낸 결과물이 없거나 내가 맡은 의뢰가 아님 */
  | { status: "notFound" }
  | { status: "error" };

/** 최신 결과물을 불러온다. 401 · 404 만 따로 알리고 나머지는 error (다시 시도) */
export async function loadLatestSubmission(jobId: number): Promise<LatestSubmissionResult> {
  try {
    return { status: "loaded", submission: await fetchLatestSubmission(jobId) };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 404) return { status: "notFound" };
    }
    return { status: "error" };
  }
}

/** 제출 · 수정 요청 시각(UTC, 오프셋 없음)의 한국 날짜 ("2026-10-07T15:22:05" → "2026-10-08") */
export const submissionDay = (dateTime: string) => koreaDateOfUtc(dateTime);

/** 이번 수정 요청이 마지막인지. 수정 요청은 결과물마다 한 번이고, revisionNumber + 1 번째 요청이다 */
export function isLastRevision(submission: LatestSubmission, revisionLimit: number): boolean {
  return submission.revisionNumber + 1 >= revisionLimit;
}

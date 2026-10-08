import { ApiError } from "../../../api/client";
import { koreaDate } from "../../../lib/date";
import { fetchLatestSubmission, fetchSubmissionHistory } from "../api/submissionApi";
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

export type SubmissionHistoryResult =
  | { status: "loaded"; submissions: LatestSubmission[] }
  | { status: "unauthorized" }
  /** 404 — 내가 맡은 의뢰가 아님 */
  | { status: "notFound" }
  | { status: "error" };

/** 내가 낸 모든 초안 · 수정안과 각 수정 요청을 불러온다 (GET /jobs/{id}/submissions) */
export async function loadSubmissionHistory(jobId: number): Promise<SubmissionHistoryResult> {
  try {
    return { status: "loaded", submissions: await fetchSubmissionHistory(jobId) };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 404) return { status: "notFound" };
    }
    return { status: "error" };
  }
}

/** 제출 · 수정 요청 시각의 한국 날짜 ("2026-10-08T00:22:05+09:00" → "2026-10-08") */
export const submissionDay = (dateTime: string) => koreaDate(dateTime);

/** 이번 수정 요청이 마지막인지. 수정 요청은 결과물마다 한 번이고, revisionNumber + 1 번째 요청이다 */
export function isLastRevision(submission: LatestSubmission, revisionLimit: number): boolean {
  return submission.revisionNumber + 1 >= revisionLimit;
}

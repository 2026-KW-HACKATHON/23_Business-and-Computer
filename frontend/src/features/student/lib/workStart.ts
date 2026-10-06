import { ApiError } from "../../../api/client";
import { startProposalJob } from "../api/workStartApi";

/** 작업 시작 결과 */
export type WorkStartResult =
  | { status: "started"; draftDeadline: string }
  | {
      status:
        | "unauthorized"
        /** 403 JOB_START_403 — 이 제안을 보낸 학생이 아님 */
        | "forbidden"
        /** 404 JOB_404 */
        | "notFound"
        /** 409 JOB_START_409 — 시작할 수 없는 상태 (취소됨 등) */
        | "notAvailable"
        /** 5xx · 네트워크 */
        | "error";
    };

/** 작업을 시작하고 결과를 화면이 쓰는 값으로 바꾼다 */
export async function sendWorkStart(jobId: number): Promise<WorkStartResult> {
  try {
    const answer = await startProposalJob(jobId);
    return { status: "started", draftDeadline: answer.draftDeadline };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.code === "JOB_START_403") return { status: "forbidden" };
      if (error.code === "JOB_404") return { status: "notFound" };
      if (error.code === "JOB_START_409") return { status: "notAvailable" };
    }
    return { status: "error" };
  }
}

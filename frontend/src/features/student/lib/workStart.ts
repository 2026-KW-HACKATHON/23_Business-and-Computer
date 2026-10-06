import { ApiError } from "../../../api/client";
import { declineProposalJob, startProposalJob } from "../api/workStartApi";

/** 작업 시작 결과 */
export type WorkStartResult =
  /** chatRoomId: 작업과 함께 열린 채팅방. 서버가 주지 않으면 없다 */
  | { status: "started"; draftDeadline: string; chatRoomId: string | undefined }
  | {
      status:
        | "unauthorized"
        /** 403 JOB_START_403 — 이 제안을 보낸 학생이 아님 */
        | "forbidden"
        /** 404 JOB_404 */
        | "notFound"
        /** 409 JOB_START_409 — 시작할 수 없는 상태 (성사되지 않음 등) */
        | "notAvailable"
        /** 5xx · 네트워크 */
        | "error";
    };

/** 작업을 시작하고 결과를 화면이 쓰는 값으로 바꾼다 */
export async function sendWorkStart(jobId: number): Promise<WorkStartResult> {
  try {
    const answer = await startProposalJob(jobId);
    return {
      status: "started",
      draftDeadline: answer.draftDeadline,
      chatRoomId: answer.chatRoomId ?? undefined,
    };
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

/** 의뢰서 거절 결과 */
export type WorkDeclineResult =
  | { status: "declined" }
  | {
      status:
        | "unauthorized"
        /** 403 JOB_DECLINE_403 — 이 제안을 보낸 학생이 아님 */
        | "forbidden"
        /** 404 JOB_404 */
        | "notFound"
        /** 409 JOB_DECLINE_409 — 이미 시작했거나 거절 · 취소된 의뢰 */
        | "notAvailable"
        /** 5xx · 네트워크 */
        | "error";
    };

/** 의뢰서를 거절하고 결과를 화면이 쓰는 값으로 바꾼다 */
export async function sendWorkDecline(jobId: number): Promise<WorkDeclineResult> {
  try {
    await declineProposalJob(jobId);
    return { status: "declined" };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.code === "JOB_DECLINE_403") return { status: "forbidden" };
      if (error.code === "JOB_404") return { status: "notFound" };
      if (error.code === "JOB_DECLINE_409") return { status: "notAvailable" };
    }
    return { status: "error" };
  }
}

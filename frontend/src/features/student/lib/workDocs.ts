import type { ChatWorkDoc, ChatWorkEntry, ChatWorkStage } from "../../chat";
import type { WorkKind } from "../../../types/workKind";
import { STUDENT_PATHS } from "./paths";

/**
 * 작업 이력 줄마다 학생이 여는 화면 (ADR 0045). 지금 서류(낸 결과물 · 고치는 중의 수정 요청)는 그 단계 화면,
 * 지난 초안 · 수정안 · 수정 요청은 서류 이력의 그 결과물로 연다. 이력을 불러오지 못한 지난 서류는 없다
 */
export function studentWorkDocPath(
  entry: ChatWorkEntry,
  jobId: number,
  stage: ChatWorkStage | undefined,
  proposalId?: number,
): string | undefined {
  const id = String(jobId);
  const submissionId = entry.submissionId === undefined ? undefined : String(entry.submissionId);
  switch (entry.doc) {
    case "start":
      return proposalId !== undefined ? STUDENT_PATHS.proposal(String(proposalId)) : STUDENT_PATHS.requestFull(id);
    case "draft":
      if (!entry.past && stage === "draftArrived") return STUDENT_PATHS.workSubmitted(id);
      return submissionId === undefined ? undefined : STUDENT_PATHS.workSubmission(id, submissionId);
    case "revision":
      if (!entry.past && stage === "revisionArrived") return STUDENT_PATHS.workSubmitted(id);
      return submissionId === undefined ? undefined : STUDENT_PATHS.workSubmission(id, submissionId);
    case "revisionRequest":
      if (!entry.past && stage === "revising") return STUDENT_PATHS.workRevision(id);
      return submissionId === undefined ? undefined : STUDENT_PATHS.workSubmissionRequest(id, submissionId);
    case "result":
      return STUDENT_PATHS.workResult(id);
    case "review":
      return STUDENT_PATHS.workReview(id);
    case "canceled":
      return STUDENT_PATHS.workCanceled(id);
  }
}

/** 작업 이력 줄의 회색 설명 (날짜 없음) */
export function studentWorkDocSub(doc: ChatWorkDoc, kind: WorkKind): string {
  switch (doc) {
    case "start":
      return kind === "proposal" ? "보낸 제안 · 사장님이 정한 조건" : "작업 조건 · 내 작업계획서";
    case "draft":
      return "제출한 초안";
    case "revisionRequest":
      return "사장님이 보낸 수정 요청";
    case "revision":
      return "제출한 수정안";
    case "result":
      return "완료된 결과물";
    case "review":
      return "받은 후기";
    case "canceled":
      return "성사되지 않은 작업";
  }
}

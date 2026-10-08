import type { ChatWorkDoc, ChatWorkStage } from "../../chat";
import type { WorkKind } from "../../../types/workKind";
import { STUDENT_PATHS } from "./paths";

/**
 * 작업 서류마다 학생이 여는 화면. 마지막으로 낸 결과물과 그 수정 요청만 서버가 주어서, 그 앞의 지난
 * 초안 · 수정 요청은 없다 (서류 이력 API 요청 중, ADR 0045)
 */
export function studentWorkDocPath(
  doc: ChatWorkDoc,
  jobId: number,
  stage: ChatWorkStage | undefined,
  proposalId?: number,
): string | undefined {
  const id = String(jobId);
  switch (doc) {
    case "start":
      return proposalId !== undefined ? STUDENT_PATHS.proposal(String(proposalId)) : STUDENT_PATHS.requestFull(id);
    case "draft":
      return stage === "draftArrived" || stage === "revising" ? STUDENT_PATHS.workSubmitted(id) : undefined;
    case "revisionRequest":
      return stage === "revising" ? STUDENT_PATHS.workRevision(id) : undefined;
    case "revision":
      return stage === "revisionArrived" ? STUDENT_PATHS.workSubmitted(id) : undefined;
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

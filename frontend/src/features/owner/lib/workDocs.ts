import type { ChatWorkDoc, ChatWorkEntry, ChatWorkStage } from "../../chat";
import type { WorkKind } from "../../../types/workKind";
import { OWNER_PATHS } from "./paths";

/**
 * 작업 이력 줄마다 사장님이 여는 화면 (ADR 0045). 지금 서류(도착한 결과물 · 고치는 중의 수정 요청)는 그 단계
 * 화면, 지난 초안 · 수정안 · 수정 요청은 서류 이력의 그 결과물로 연다. 이력을 불러오지 못한 지난 서류는 없다
 */
export function ownerWorkDocPath(
  entry: ChatWorkEntry,
  jobId: number,
  stage: ChatWorkStage | undefined,
  proposalId?: number,
): string | undefined {
  const id = String(jobId);
  const submissionId = entry.submissionId === undefined ? undefined : String(entry.submissionId);
  switch (entry.doc) {
    case "start":
      return proposalId !== undefined ? OWNER_PATHS.proposal(String(proposalId)) : OWNER_PATHS.request(id);
    case "draft":
      if (!entry.past && stage === "draftArrived") return OWNER_PATHS.workCheck(id);
      return submissionId === undefined ? undefined : OWNER_PATHS.workSubmission(id, submissionId);
    case "revision":
      if (!entry.past && stage === "revisionArrived") return OWNER_PATHS.workCheck(id);
      return submissionId === undefined ? undefined : OWNER_PATHS.workSubmission(id, submissionId);
    case "revisionRequest":
      if (!entry.past && stage === "revising") return OWNER_PATHS.workRevisionSent(id);
      return submissionId === undefined ? undefined : OWNER_PATHS.workRevisionSent(id, submissionId);
    case "result":
      return OWNER_PATHS.workResult(id);
    case "review":
      return OWNER_PATHS.workReviewView(id);
    case "canceled":
      return OWNER_PATHS.workCanceled(id);
  }
}

/** 작업 이력 줄의 회색 설명 (날짜 없음) */
export function ownerWorkDocSub(doc: ChatWorkDoc, kind: WorkKind, stage: ChatWorkStage | undefined): string {
  switch (doc) {
    case "start":
      return kind === "proposal" ? "학생 제안 · 정한 작업 조건" : "작업 조건 · 학생 작업계획서";
    case "draft":
      return stage === "draftArrived" ? "도착했어요, 확인해 주세요" : "수정을 요청한 초안";
    case "revisionRequest":
      return "학생에게 보낸 수정 요청";
    case "revision":
      return stage === "revisionArrived" ? "도착했어요, 확인해 주세요" : "받은 수정안";
    case "result":
      return "완료된 결과물";
    case "review":
      return "남긴 후기";
    case "canceled":
      return "성사되지 않은 작업";
  }
}

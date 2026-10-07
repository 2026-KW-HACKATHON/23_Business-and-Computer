import type { ChatWorkDoc, ChatWorkStage } from "../../chat";
import type { WorkKind } from "../../../types/workKind";
import { OWNER_PATHS } from "./paths";

/**
 * 작업 서류마다 사장님이 여는 화면. 지난 초안 · 지난 수정안 · 지난 수정 요청 · 남긴 후기는 서버가 아직
 * 주지 않아 없다 (서류 이력 API 요청 중, ADR 0045)
 */
export function ownerWorkDocPath(
  doc: ChatWorkDoc,
  jobId: number,
  stage: ChatWorkStage | undefined,
  proposalId?: number,
): string | undefined {
  const id = String(jobId);
  switch (doc) {
    case "start":
      return proposalId !== undefined ? OWNER_PATHS.proposal(String(proposalId)) : OWNER_PATHS.request(id);
    case "draft":
      return stage === "draftArrived" ? OWNER_PATHS.workCheck(id) : undefined;
    case "revision":
      return stage === "revisionArrived" ? OWNER_PATHS.workCheck(id) : undefined;
    case "result":
      return OWNER_PATHS.workResult(id);
    case "canceled":
      return OWNER_PATHS.workCanceled(id);
    case "revisionRequest":
      return stage === "revising" ? OWNER_PATHS.workRevisionSent(id) : undefined;
    case "review":
      return undefined;
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

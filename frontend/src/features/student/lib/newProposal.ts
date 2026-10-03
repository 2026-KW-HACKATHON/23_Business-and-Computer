import type { Field } from "../../../types/field";
import type { WorkFile } from "../types";

/** 제안 보내기 2/4 에서 고른 일 하나 */
export interface PickedTask {
  field: Field;
  task: string;
}

/** 제안 보내기 3/4 에서 적는 내용 */
export interface ProposalContent {
  title: string;
  /** 손님 눈으로 본 문제 */
  problem: string;
  /** 이렇게 바꿔 드릴게요 */
  solution: string;
  /** 작업계획서 */
  plan: string;
  /** 원. 아직 안 적었으면 0 */
  wishBudget: number;
  /** 수락된 날부터 초안 · 최종까지 걸리는 날 (0 이면 아직 안 적음) */
  draftDays: number;
  finalDays: number;
  /** 참고 사진 (이름 · 크기만, 올리기는 백엔드 연동 때) */
  photos: WorkFile[];
}

/**
 * 제안 보내기 1/4 → 2/4 → 3/4 → 4/4 사이에 router state 로 넘기는 값.
 * 다음 단계로 가기 전에 지금 화면 기록에도 저장해 두어서 ← 로 돌아와도 고친 값이 남는다.
 */
export interface NewProposalState {
  /** 홈 예시 카드로 들어왔을 때 그 예시 id */
  exampleId?: string;
  storeId?: string;
  fields: Field[];
  picked: PickedTask[];
  content?: ProposalContent;
}

/** router state 를 읽는다. 주소로 바로 들어와 값이 없으면 undefined */
export function readNewProposalState(state: unknown): NewProposalState | undefined {
  if (!state || typeof state !== "object") return undefined;
  const value = state as Partial<NewProposalState>;
  if (!Array.isArray(value.fields) || !Array.isArray(value.picked)) {
    // 가게 탐색 「제안하기」 · 홈 예시는 고른 일 없이 들어온다
    if (typeof value.storeId === "string" || typeof value.exampleId === "string") {
      return { ...value, fields: [], picked: [] };
    }
    return undefined;
  }
  return value as NewProposalState;
}

/** 「메뉴판·가격표 디자인, 영어 번역」. 고른 일이 없으면 (기타만) 분야 이름 */
export function proposalTaskSummary({ fields, picked }: NewProposalState): string {
  return picked.length > 0 ? picked.map((p) => p.task).join(", ") : fields.join(", ");
}

/** 「초안 2일 · 최종 4일」 */
export function expectedDaysText(draftDays: number, finalDays: number): string {
  return `초안 ${draftDays}일 · 최종 ${finalDays}일`;
}

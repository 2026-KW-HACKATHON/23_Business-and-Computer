import { todayIsoDate } from "../../../lib/date";
import type { Field } from "../../../types/field";
import { SPECIALTY_BADGES } from "../../../types/specialty";
import type { DueDates, PickedTask, RequestContent } from "../types";

/**
 * 의뢰 등록 1/3 → 2/3 → 3/3 사이에 router state 로 넘기는 값.
 * 다음 단계로 가기 전에 지금 화면 기록에도 저장해 두어서 ← 로 돌아와도 고친 값이 남는다.
 */
export interface NewRequestState {
  /** 홈 예시 카드로 들어왔을 때 그 예시 id */
  exampleId?: string;
  fields: Field[];
  picked: PickedTask[];
  /** 2/3 에서 적은 내용 */
  content?: RequestContent;
}

/** router state 를 읽는다. 주소로 바로 들어와 값이 없으면 undefined */
export function readNewRequestState(state: unknown): NewRequestState | undefined {
  if (!state || typeof state !== "object") return undefined;
  const value = state as Partial<NewRequestState>;
  if (!Array.isArray(value.fields) || !Array.isArray(value.picked)) return undefined;
  return value as NewRequestState;
}

/** 의뢰서 「할 일」 줄. 고른 일이 없으면 (기타만 고른 경우) 분야 이름 */
export function taskSummary({ fields, picked }: NewRequestState): string {
  return picked.length > 0 ? picked.map((p) => p.task).join(", ") : fields.join(", ");
}

/** 두 마감일을 다 고르고 최종 마감이 초안 마감보다 앞서지 않는지 */
export function dueDatesReady({ draftDue, finalDue }: DueDates): boolean {
  return draftDue !== "" && finalDue !== "" && draftDue >= todayIsoDate() && finalDue >= draftDue;
}

/** 「우리 가게에도 비슷한 의뢰 만들기」: 같은 분야 · 같은 일이 골라진 의뢰 등록 1/3 */
export function similarRequestState(field: Field, tasks: string[] = []): NewRequestState {
  const known = SPECIALTY_BADGES.find((group) => group.field === field)?.badges ?? [];
  return {
    fields: [field],
    picked: tasks.filter((task) => known.includes(task)).map((task) => ({ field, task })),
  };
}

import type { FlowStep } from "../../../components";
import type { StudentWork } from "../types";

const LATER_STEPS = ["시작", "초안", "수정", "완료"];

/**
 * 흐름 막대 5단계. current 앞은 지남, current 는 지금(sub 를 붙임), 뒤는 아직.
 * current 가 5 이면 모두 지남.
 */
export function flowSteps(first: "의뢰" | "제안", current: number, sub?: string): FlowStep[] {
  return [first, ...LATER_STEPS].map((label, i) => ({
    label,
    state: i < current ? "done" : i === current ? "current" : "todo",
    sub: i === current ? sub : undefined,
  }));
}

/** 작업 하나의 흐름 막대 (의뢰 · 제안 → 시작 → 초안 → 수정 → 완료) */
export function workFlowSteps(work: StudentWork, sub?: string): FlowStep[] {
  const first = work.kind === "proposal" ? "제안" : "의뢰";
  switch (work.status) {
    case "awaitingAgreement":
      return flowSteps(first, 1, sub);
    case "drafting":
      return flowSteps(first, 2, sub);
    case "revising":
      return flowSteps(first, 3, sub);
    case "submitted":
      return flowSteps(first, work.revisionCount > 0 ? 3 : 2, sub);
    default:
      return flowSteps(first, 5);
  }
}

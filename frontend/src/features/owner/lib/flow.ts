import type { FlowStep } from "../../../components";

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

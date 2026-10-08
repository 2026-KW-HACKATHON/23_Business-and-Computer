import { formatMonthDay } from "../../../lib/date";
import type { DeadlineStage } from "../types";

/** 「초안 마감 : 9월 29일」 · 「최종 마감 : 10월 3일」 */
export function deadlineText(stage: DeadlineStage, due: string): string {
  return `${stage === "draft" ? "초안" : "최종"} 마감 : ${formatMonthDay(due)}`;
}

/** 「★ 4.8 · 완료 3건」. 후기가 없으면(평균 0 · 없음) 「완료 3건」, 끝낸 작업이 없으면 「첫 작업이에요」 */
export function peerRecord({
  rating,
  completedCount,
}: {
  rating?: number | null;
  completedCount: number;
}): string {
  if (completedCount === 0) return "첫 작업이에요";
  if (!rating) return `완료 ${completedCount}건`;
  return `★ ${rating.toFixed(1)} · 완료 ${completedCount}건`;
}

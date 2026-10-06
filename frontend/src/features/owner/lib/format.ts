import { formatMonthDay } from "../../../lib/date";
import type {
  DeadlineStage,
  StudentProfileRef,
  StudentRef,
  WaitingStatus,
} from "../types";
import { studentTitle } from "../../../lib/korean";

/** 「시각디자인학과 박지은 학생」 · 학과가 없으면 「김광운 학생」 */
export function studentLabel({ name, department }: StudentRef): string {
  return department ? `${department} ${studentTitle(name)}` : studentTitle(name);
}

/** 「초안 마감 : 9월 29일」 · 「최종 마감 : 10월 3일」 */
export function deadlineText(stage: DeadlineStage, due: string): string {
  return `${stage === "draft" ? "초안" : "최종"} 마감 : ${formatMonthDay(due)}`;
}

export const WAITING_STATUS_LABEL: Record<WaitingStatus, string> = {
  recruiting: "학생 모집 중",
};

/** 「★ 4.8 · 완료 3건」 · 후기가 없으면 「첫 작업이에요」 */
export function studentRecord({ rating, completedCount }: StudentProfileRef): string {
  if (completedCount === 0 || rating === undefined) return "첫 작업이에요";
  return `★ ${rating.toFixed(1)} · 완료 ${completedCount}건`;
}


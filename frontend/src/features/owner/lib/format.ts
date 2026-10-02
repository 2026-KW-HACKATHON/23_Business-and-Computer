import { formatMonthDay } from "../../../lib/date";
import type { DeadlineStage, StudentRef, WaitingStatus } from "../types";

/** 「시각디자인학과 박지은 학생」 · 학과가 없으면 「김광운 학생」 */
export function studentLabel({ name, department }: StudentRef): string {
  return department ? `${department} ${name} 학생` : `${name} 학생`;
}

/** 「초안 마감 : 9월 29일」 · 「최종 마감 : 10월 3일」 */
export function deadlineText(stage: DeadlineStage, due: string): string {
  return `${stage === "draft" ? "초안" : "최종"} 마감 : ${formatMonthDay(due)}`;
}

export const WAITING_STATUS_LABEL: Record<WaitingStatus, string> = {
  recruiting: "학생 모집 중",
};

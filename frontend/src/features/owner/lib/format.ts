import { formatMonthDay } from "../../../lib/date";
import type {
  ChatProgress,
  DeadlineStage,
  OwnerWork,
  StudentProfileRef,
  StudentRef,
  WaitingStatus,
  WorkPlanSheetContent,
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

/** 채팅 목록의 굵은 진행 상태 */
export function chatProgressText(progress: ChatProgress): string {
  switch (progress.type) {
    case "drafting":
      return `초안 만드는 중 (~${formatMonthDay(progress.due)})`;
    case "revising":
      return `수정안 만드는 중 (~${formatMonthDay(progress.due)})`;
    case "draftSubmitted":
      return "초안을 확인해 주세요";
    case "completed":
      return "완료";
  }
}

/** 「★ 4.8 · 완료 3건」 · 후기가 없으면 「첫 작업이에요」 */
export function studentRecord({ rating, completedCount }: StudentProfileRef): string {
  if (completedCount === 0 || rating === undefined) return "첫 작업이에요";
  return `★ ${rating.toFixed(1)} · 완료 ${completedCount}건`;
}

/** 채팅방 위 작업 카드의 굵은 진행 상태 */
export function workChatSummary(work: OwnerWork): string {
  if (work.status === "completed") return "완료된 작업이에요";
  if (work.status === "submitted") {
    return `${work.revisionCount > 0 ? "수정안" : "초안"}이 도착했어요, 확인해 주세요`;
  }
  return work.revisionCount > 0
    ? `수정안 만드는 중, ${formatMonthDay(work.finalDue)}까지 도착`
    : `초안 만드는 중, ${formatMonthDay(work.draftDue)}까지 도착`;
}

/** 샘플 작업의 작업계획서 바텀시트 내용 */
export function ownerWorkPlanContent(work: OwnerWork): WorkPlanSheetContent {
  return {
    title: work.title,
    studentName: work.student.name,
    sentOn: work.planSentOn,
    plan: work.plan,
    budget: work.budget,
    draftDue: work.draftDue,
    finalDue: work.finalDue,
    revisionLimit: work.revisionLimit,
  };
}

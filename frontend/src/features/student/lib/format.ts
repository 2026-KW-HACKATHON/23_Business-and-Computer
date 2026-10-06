import { formatMonthDay } from "../../../lib/date";
import type { DeadlineStage, StudentWork } from "../types";

/** 「초안 마감 : 9월 29일」 · 「최종 마감 : 10월 3일」 */
export function deadlineText(stage: DeadlineStage, due: string): string {
  return `${stage === "draft" ? "초안" : "최종"} 마감 : ${formatMonthDay(due)}`;
}

/** 지금 지켜야 할 마감. 초안을 낸 뒤나 수정 요청을 받은 뒤에는 최종 마감 */
export function currentDeadline(work: StudentWork): { stage: DeadlineStage; due: string } {
  const afterDraft = work.status === "revising" || work.status === "submitted";
  return afterDraft ? { stage: "final", due: work.finalDue } : { stage: "draft", due: work.draftDue };
}

/** 내 활동 · 진행 중 카드의 진행 상태 */
export function workStatusText(work: StudentWork): string {
  switch (work.status) {
    case "awaitingAgreement":
      return "의뢰서가 도착했어요";
    case "drafting":
      return "초안 제작 중";
    case "revising":
      return "수정 요청이 왔어요";
    case "submitted":
      return `${work.revisionCount > 0 ? "수정안" : "초안"} 제출, 사장님 확인 중`;
    case "completed":
      return "완료";
    case "canceled":
      return "성사되지 않음";
  }
}

/** 채팅 목록의 굵은 진행 상태 (사장님 채팅 목록과 같은 모양) */
export function chatStatusText(work: StudentWork): string {
  const stage = work.revisionCount > 0 ? "수정안" : "초안";
  switch (work.status) {
    case "drafting":
      return `초안 만드는 중 (~${formatMonthDay(work.draftDue)})`;
    case "revising":
      return `수정안 만드는 중 (~${formatMonthDay(work.finalDue)})`;
    case "submitted":
      return `사장님이 ${stage} 확인 중`;
    case "completed":
      return "완료";
    default:
      return "";
  }
}

/** 채팅방 위 작업 카드의 굵은 진행 상태 (사장님 채팅방과 같은 모양) */
export function workChatSummary(work: StudentWork): string {
  switch (work.status) {
    case "drafting":
      return `초안 만드는 중, ${formatMonthDay(work.draftDue)}까지 제출`;
    case "revising":
      return `수정안 만드는 중, ${formatMonthDay(work.finalDue)}까지 제출`;
    case "submitted":
      return `${work.revisionCount > 0 ? "수정안" : "초안"}을 보냈어요, 사장님 확인 중`;
    case "completed":
      return "완료된 작업이에요";
    default:
      return "";
  }
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

import { useSyncExternalStore } from "react";
import { todayIsoDate } from "../../../lib/date";
import type { ApplicationPlan, MyProposal, WorkFile } from "../types";

/*
 * 시연 중에 바뀐 상태. 백엔드 연동 전까지 새로고침하면 처음으로 돌아간다.
 * 바뀔 때마다 version 이 올라가 이 상태를 읽는 화면이 다시 그려진다.
 */

interface Submission {
  files: WorkFile[];
  message: string;
  on: string;
}

export const demo = {
  agreedWorkIds: new Set<string>(),
  /** 동의한 시각 (채팅방 첫 메시지) */
  agreedAt: new Map<string, string>(),
  readNotificationIds: new Set<string>(),
  declinedWorkIds: new Set<string>(),
  submissions: new Map<string, Submission>(),
  toggledEmpathyIds: new Set<string>(),
  canceledProposalIds: new Set<string>(),
  sentProposals: [] as MyProposal[],
  applications: new Map<string, { plan: ApplicationPlan; on: string }>(),
};

let version = 0;
const listeners = new Set<() => void>();

function changed() {
  version += 1;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

const getVersion = () => version;

/** 상태가 바뀌면 다시 그리게 한다 */
export function useDemoVersion(): number {
  return useSyncExternalStore(subscribe, getVersion);
}

/** 작업 시작 「동의하고 작업 시작하기」 */
export function agreeToWork(workId: string): void {
  demo.agreedWorkIds.add(workId);
  demo.agreedAt.set(workId, new Date().toISOString());
  changed();
}

/** 알림을 읽음으로 (알림 화면 · 홈의 안 읽음 점이 같이 본다) */
export function markNotificationsRead(ids: string[]): void {
  ids.forEach((id) => demo.readNotificationIds.add(id));
  changed();
}

/** 작업 시작 「이 조건은 어려워요」 → 거절 (사장님께 작업비가 돌아간다) */
export function declineWork(workId: string): void {
  demo.declinedWorkIds.add(workId);
  changed();
}

/** 초안 · 수정안 제출 */
export function submitWork(workId: string, files: WorkFile[], message: string): void {
  demo.submissions.set(workId, { files, message, on: todayIsoDate() });
  changed();
}

/** 다른 학생 제안에 공감 켜기 · 끄기 */
export function toggleEmpathy(proposalId: string): void {
  if (demo.toggledEmpathyIds.has(proposalId)) demo.toggledEmpathyIds.delete(proposalId);
  else demo.toggledEmpathyIds.add(proposalId);
  changed();
}

/** 보낸 제안 취소 */
export function cancelMyProposal(proposalId: string): void {
  demo.canceledProposalIds.add(proposalId);
  changed();
}

/** 제안 보내기 완료 */
export function sendProposal(proposal: MyProposal): void {
  demo.sentProposals = [proposal, ...demo.sentProposals];
  changed();
}

/** 지원서 보내기 */
export function applyToRequest(requestId: string, plan: ApplicationPlan): void {
  demo.applications.set(requestId, { plan, on: todayIsoDate() });
  changed();
}

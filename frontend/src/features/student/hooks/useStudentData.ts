import { addDays, todayIsoDate } from "../../../lib/date";
import { SAMPLE_MY_PROPOSALS, SAMPLE_PROPOSAL_EXAMPLES } from "../lib/sampleProposals";
import { SAMPLE_STORES } from "../lib/sampleStores";
import { SAMPLE_WORKS } from "../lib/sampleWorks";
import type { MyProposal, ProposalExample, Store, StudentWork } from "../types";
import { demo, useDemoVersion } from "./studentStore";

/*
 * 학생 화면 데이터. 지금은 임시 예시 데이터를 돌려준다.
 * 백엔드를 연동할 때 이 안만 API 호출로 바꾸면 화면은 그대로 쓴다.
 *
 * 원본은 작업 · 의뢰 · 지원 · 제안 하나씩이고, 내 활동 완료 탭과 내 작업물은 작업에서 만든다.
 * 내 정보 · 프로필 · 정산 내역은 서버를 읽는다 (useStudentMe · useSettlementHistory).
 */

// ---- 원본에 시연 중 바뀐 상태를 얹는다 ----

function currentWork(work: StudentWork): StudentWork {
  let next = work;
  if (next.status === "awaitingAgreement" && demo.agreedWorkIds.has(next.id)) {
    const today = todayIsoDate();
    next = {
      ...next,
      status: "drafting",
      startedOn: today,
      history: [...next.history, { date: today, text: "조건 동의 · 작업 시작" }],
    };
  }
  const submission = demo.submissions.get(next.id);
  if (submission && (next.status === "drafting" || next.status === "revising")) {
    const stage = next.status === "revising" ? "수정안" : "초안";
    next = {
      ...next,
      status: "submitted",
      files: submission.files,
      myMessage: submission.message,
      submittedOn: submission.on,
      autoCompleteOn: addDays(submission.on, 7),
      history: [...next.history, { date: submission.on, text: `${stage} 제출` }],
    };
  }
  return next;
}

const works = () =>
  SAMPLE_WORKS.filter((w) => !demo.declinedWorkIds.has(w.id)).map(currentWork);

function currentProposals(): MyProposal[] {
  // 의뢰서를 거절했거나 동의해 작업이 시작된 제안은 보낸 제안에서 빠진다
  const settled = (workId?: string) =>
    workId !== undefined && (demo.declinedWorkIds.has(workId) || demo.agreedWorkIds.has(workId));
  return SAMPLE_MY_PROPOSALS.filter((p) => !settled(p.workId));
}

// ---- 작업 ----

export function useStudentWorks(): StudentWork[] {
  useDemoVersion();
  return works();
}

export function useStudentWork(workId: string | undefined): StudentWork | undefined {
  useDemoVersion();
  return works().find((w) => w.id === workId);
}

// ---- 제안 ----

export function useMyProposal(proposalId: string | undefined): MyProposal | undefined {
  useDemoVersion();
  return currentProposals().find((p) => p.id === proposalId);
}

export function useProposalExample(exampleId: string | undefined): ProposalExample | undefined {
  return SAMPLE_PROPOSAL_EXAMPLES.find((e) => e.id === exampleId);
}

export function useProposalExamples(): ProposalExample[] {
  return SAMPLE_PROPOSAL_EXAMPLES;
}

// ---- 가게 ----

export function useStores(): Store[] {
  return SAMPLE_STORES;
}

export function useStore(storeId: string | undefined): Store | undefined {
  return SAMPLE_STORES.find((s) => s.id === storeId);
}


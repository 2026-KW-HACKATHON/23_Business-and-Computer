import { addDays, todayIsoDate } from "../../../lib/date";
import type { Field } from "../../../types/field";
import { SPECIALTY_BADGES } from "../../../types/specialty";
import { SAMPLE_MY_PROFILE } from "../lib/sampleMe";
import { SAMPLE_NOTIFICATIONS } from "../lib/sampleNotifications";
import { SAMPLE_MY_PROPOSALS, SAMPLE_PROPOSAL_EXAMPLES } from "../lib/sampleProposals";
import { SAMPLE_STORES } from "../lib/sampleStores";
import { SAMPLE_WORKS } from "../lib/sampleWorks";
import type {
  MyProfile,
  MyProposal,
  OwnerReview,
  ProposalExample,
  SettlementSummary,
  Store,
  StudentNotification,
  StudentSettlement,
  StudentWork,
} from "../types";
import { demo, useDemoVersion } from "./studentStore";

/*
 * 학생 화면 데이터. 지금은 임시 예시 데이터를 돌려준다.
 * 백엔드를 연동할 때 이 안만 API 호출로 바꾸면 화면은 그대로 쓴다.
 *
 * 원본은 작업 · 의뢰 · 지원 · 제안 · 프로필 하나씩이고, 홈 · 내 활동 숫자 · 정산 내역 ·
 * 내 작업물 · 프로필의 완료 건수와 평점은 원본에서 만든다.
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

// ---- 내 프로필: 완료 건수 · 평점 · 후기는 작업에서 ----

export interface ReceivedReview extends OwnerReview {
  workId: string;
  storeName: string;
  workTitle: string;
}

export interface MyProfileView extends MyProfile {
  /** 고른 뱃지의 대분류 (내 정보의 「디자인 / 홍보」) */
  fields: Field[];
  completedCount: number;
  /** 함께한 가게 수 */
  storeCount: number;
  /** 후기 평균. 후기가 없으면 비운다 */
  rating?: number;
  /** 보낸 제안 수 (수락돼 작업이 된 것까지) */
  proposalCount: number;
  /** 최근 것부터 */
  reviews: ReceivedReview[];
}

export function useMyProfile(): MyProfileView {
  useDemoVersion();
  const all = works();
  const completed = all.filter((w) => w.status === "completed");
  const reviews: ReceivedReview[] = completed
    .flatMap((w) =>
      w.review ? [{ ...w.review, workId: w.id, storeName: w.store.name, workTitle: w.title }] : [],
    )
    .sort((a, b) => b.date.localeCompare(a.date));
  const rating =
    reviews.length > 0
      ? Math.round((reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length) * 10) / 10
      : undefined;
  const proposals = currentProposals();
  const fromWorks = all.filter(
    (w) => w.proposalId && !proposals.some((p) => p.id === w.proposalId),
  ).length;
  const profile = { ...SAMPLE_MY_PROFILE, ...demo.profile };
  return {
    ...profile,
    fields: SPECIALTY_BADGES.filter((group) =>
      group.badges.some((badge) => profile.badges.includes(badge)),
    ).map((group) => group.field),
    completedCount: completed.length,
    storeCount: new Set(completed.map((w) => w.store.id)).size,
    rating,
    proposalCount: proposals.length + fromWorks,
    reviews,
  };
}

// ---- 알림 ----

export function useStudentNotifications(): StudentNotification[] {
  useDemoVersion();
  return SAMPLE_NOTIFICATIONS.map((n) => (demo.readNotificationIds.has(n.id) ? { ...n, read: true } : n)).sort(
    (a, b) => b.createdAt.localeCompare(a.createdAt),
  );
}

// ---- 정산 내역: 작업에서 만든다 ----

export function useStudentSettlements(): {
  settlements: StudentSettlement[];
  summary: SettlementSummary;
} {
  useDemoVersion();
  const settlements: StudentSettlement[] = works()
    .flatMap((w): StudentSettlement[] => {
      const base = { workId: w.id, title: w.title, storeName: w.store.name };
      if (w.status === "completed" && w.completedOn) {
        return [
          {
            ...base,
            amount: w.budget,
            status: "settled",
            date: w.completedOn,
            autoCompleted: w.completedBy === "auto",
          },
        ];
      }
      if (w.status === "canceled" && w.cancel && w.cancel.reward > 0) {
        return [{ ...base, amount: w.cancel.reward, status: "reward", date: w.cancel.canceledOn }];
      }
      if (["drafting", "revising", "submitted"].includes(w.status) && w.startedOn) {
        return [{ ...base, amount: w.budget, status: "expected", date: w.startedOn }];
      }
      return [];
    })
    .sort((a, b) => {
      // 정산 예정을 먼저, 그다음 최근 정산부터
      if ((a.status === "expected") !== (b.status === "expected")) {
        return a.status === "expected" ? -1 : 1;
      }
      return b.date.localeCompare(a.date);
    });
  const month = todayIsoDate().slice(0, 7);
  const expected = settlements
    .filter((s) => s.status === "expected")
    .reduce((sum, s) => sum + s.amount, 0);
  const settledThisMonth = settlements
    .filter((s) => s.status !== "expected" && s.date.startsWith(month))
    .reduce((sum, s) => sum + s.amount, 0);
  return {
    settlements,
    summary: { thisMonth: expected + settledThisMonth, expected, settledThisMonth },
  };
}

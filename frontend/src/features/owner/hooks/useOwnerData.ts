import { todayIsoDate } from "../../../lib/date";
import {
  SAMPLE_REQUESTS,
  SAMPLE_WORKS,
} from "../lib/sampleDetails";
import { SAMPLE_REQUEST_EXAMPLES } from "../lib/sampleHome";
import { SAMPLE_STUDENT_PROFILES } from "../lib/sampleStudents";
import { SAMPLE_NOTIFICATIONS } from "../lib/sampleTabs";
import type {
  OwnerNotification,
  OwnerPayment,
  OwnerRequest,
  OwnerWork,
  PaymentSummary,
  RequestExample,
  StudentProfile,
  StudentProfileRef,
  StudentRef,
} from "../types";
import { ownerDemo, useOwnerDemoVersion } from "./ownerDemo";

/*
 * 사장님 화면 데이터. 지금은 임시 예시 데이터를 돌려준다.
 * 백엔드를 연동할 때 이 안만 API 호출로 바꾸면 화면은 그대로 쓴다.
 *
 * 원본은 작업 · 의뢰 · 제안 · 학생 프로필 · 탐색 상세 하나씩이고, 결제 내역 ·
 * 탐색 목록(과 useOwnerHome 의 홈)은 원본에서 만든다. 그래서 어느 화면에서
 * 상세로 들어가도 이름 · 학과 · 금액 · 날짜가 같다.
 */

// ---- 시연 중에 바뀐 상태. 백엔드 연동 전까지 새로고침하면 처음으로 돌아간다 ----

const completedWorkIds = new Set<string>();
const reviewedWorkIds = new Set<string>();

/** 작업 확인 「완료 확인」 (백엔드: complete_work) */
export function completeOwnerWork(workId: string): void {
  completedWorkIds.add(workId);
}

/** 후기를 남김 (백엔드: 후기 저장) */
export function markOwnerWorkReviewed(workId: string): void {
  reviewedWorkIds.add(workId);
}

/** 이 화면을 연 동안 후기를 남겼는지. 끝난 작업 목록이 후기 여부를 주기 전까지 서버 작업도 이것으로 본다 */
export function isOwnerWorkReviewed(workId: string): boolean {
  return reviewedWorkIds.has(workId);
}

// ---- 학생: 이름 · 학과 · 학번 · 평점은 프로필 한 곳에서 ----

/** 후기 평균. 후기가 없으면 비운다 */
function ratingOf(profile: StudentProfile): number | undefined {
  if (profile.reviews.length === 0) return undefined;
  const sum = profile.reviews.reduce((total, review) => total + review.rating, 0);
  return Math.round((sum / profile.reviews.length) * 10) / 10;
}

function profileOf(studentId: string | undefined): StudentProfile | undefined {
  const profile = SAMPLE_STUDENT_PROFILES.find((p) => p.id === studentId);
  return profile && { ...profile, rating: ratingOf(profile) };
}

function withStudent<T extends StudentRef>(ref: T): T {
  const profile = profileOf(ref.id);
  if (!profile) return ref;
  const fromProfile: StudentProfileRef = {
    id: profile.id,
    name: profile.name,
    department: profile.department,
    year: profile.year,
    rating: profile.rating,
    completedCount: profile.completedCount,
  };
  return { ...ref, ...fromProfile };
}

// ---- 원본 ----

function currentWork(work: OwnerWork): OwnerWork {
  let next: OwnerWork = { ...work, student: withStudent(work.student) };
  if (completedWorkIds.has(work.id) && work.status === "submitted") {
    const today = todayIsoDate();
    next = {
      ...next,
      status: "completed",
      completedOn: today,
      completedBy: "owner",
      autoCompleteOn: undefined,
      history: [...work.history, { date: today, text: "사장님이 완료 확인" }],
    };
  }
  return reviewedWorkIds.has(work.id) ? { ...next, reviewed: true } : next;
}

const works = () => SAMPLE_WORKS.map(currentWork);

const requests = () =>
  SAMPLE_REQUESTS.map((request) => ({
    ...request,
    applicants: request.applicants.map((a) => ({ ...a, student: withStudent(a.student) })),
  }));

// ---- 화면별 ----

/** 알림. 최근 것부터. 읽은 알림은 read 로 바꿔 준다 */
export function useOwnerNotifications(): OwnerNotification[] {
  useOwnerDemoVersion();
  return SAMPLE_NOTIFICATIONS.map((n) =>
    ownerDemo.readNotificationIds.has(n.id) ? { ...n, read: true } : n,
  );
}

/** 작업 하나. 없으면 undefined */
export function useOwnerWork(workId: string | undefined): OwnerWork | undefined {
  return works().find((work) => work.id === workId);
}

/** 홈 「이런 의뢰는 어때요?」 예시 하나. 의뢰 등록을 이 내용으로 채워 시작한다 */
export function useRequestExample(exampleId: string | undefined): RequestExample | undefined {
  return SAMPLE_REQUEST_EXAMPLES.find((example) => example.id === exampleId);
}

/** 내 작업 전체 (진행 중 · 완료 · 취소) */
export function useOwnerWorks(): OwnerWork[] {
  return works();
}

/** 보낸 의뢰 전체 (모집 중) */
export function useOwnerRequests(): OwnerRequest[] {
  useOwnerDemoVersion();
  return requests();
}

/** 작업 하나의 결제. 작업 상태가 곧 결제 상태다 */
function paymentOf(work: OwnerWork): OwnerPayment {
  const base = {
    id: `pay-${work.id}`,
    workId: work.id,
    title: work.title,
    studentName: work.student.name,
    amount: work.budget,
    paidOn: work.paidOn,
  };
  if (work.status === "completed") {
    return {
      ...base,
      status: "settled",
      settledOn: work.completedOn,
      autoCompleted: work.completedBy === "auto",
    };
  }
  if (work.status === "canceled" && work.cancel) {
    return {
      ...base,
      status: work.cancel.refund >= work.budget ? "fullRefund" : "partialRefund",
      refund: { on: work.cancel.canceledOn, amount: work.cancel.refund },
    };
  }
  return { ...base, status: "escrowed" };
}

/** 결제 내역 (최근 일부터)과 위 요약 */
export function useOwnerPayments(): { payments: OwnerPayment[]; summary: PaymentSummary } {
  const eventDate = (p: OwnerPayment) => p.refund?.on ?? p.settledOn ?? p.paidOn;
  const payments = works()
    .map(paymentOf)
    .sort((a, b) => eventDate(b).localeCompare(eventDate(a)));
  const month = todayIsoDate().slice(0, 7);
  const sum = (list: OwnerPayment[]) => list.reduce((total, p) => total + p.amount, 0);
  return {
    payments,
    summary: {
      thisMonth: sum(payments.filter((p) => p.paidOn.startsWith(month))),
      escrowed: sum(payments.filter((p) => p.status === "escrowed")),
      settled: sum(payments.filter((p) => p.status === "settled")),
    },
  };
}


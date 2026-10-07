import { ApiError } from "../../../api/client";
import { formatMonthDay, koreaDateOfUtc } from "../../../lib/date";
import { studentTitle } from "../../../lib/korean";
import { formatWon } from "../../../lib/money";
import { fetchOwnerPayments } from "../api/paymentHistoryApi";
import type { PaymentHistoryItem, PaymentHistoryResponse, PaymentHistoryStatus } from "../api/paymentHistoryApi";
import type { PaymentSummary } from "../types";

/** 결제 내역 (GET /payments): 요약과 달마다 묶은 결제 */
export type OwnerPaymentHistory = PaymentHistoryResponse;
export type OwnerPaymentItem = PaymentHistoryItem;
export type { PaymentHistoryStatus };

/** 결제 요약 3칸 (이번 달 결제 · 골목인턴이 보관 중 · 정산 완료) */
export function paymentSummaryOf(history: OwnerPaymentHistory): PaymentSummary {
  return {
    thisMonth: history.summary.thisMonthPaymentAmount,
    escrowed: history.summary.heldAmount,
    settled: history.summary.totalSettledAmount,
  };
}

/** 상태 칩 글자 */
export const PAYMENT_STATUS_LABEL: Record<PaymentHistoryStatus, string> = {
  HELD: "보관 중",
  SETTLED: "정산 완료",
  PARTIALLY_REFUNDED: "부분 환불",
  FULLY_REFUNDED: "전액 환불",
};

/**
 * 결제 한 줄의 둘째 줄. 서버는 결제한 시각만 주어서 날짜는 결제한 날이다.
 * 「김광운 학생 · 9월 20일」(보관 중) · 「… · 9월 3일 결제」(정산 완료) ·
 * 「… · 작업 중 취소 · 64,000원 환불」(부분 환불) · 「… · 80,000원 환불」(전액 환불)
 */
export function paymentDetailText(payment: OwnerPaymentItem): string {
  const student = payment.studentName?.trim() ? studentTitle(payment.studentName) : "학생";
  const paidOn = payment.approvedAt ? formatMonthDay(koreaDateOfUtc(payment.approvedAt)) : undefined;
  const refund = formatWon(payment.refundAmount ?? 0);
  switch (payment.status) {
    case "HELD":
      return [student, paidOn].filter(Boolean).join(" · ");
    case "SETTLED":
      return [student, paidOn && `${paidOn} 결제`].filter(Boolean).join(" · ");
    case "PARTIALLY_REFUNDED":
      return `${student} · 작업 중 취소 · ${refund} 환불`;
    case "FULLY_REFUNDED":
      return `${student} · ${refund} 환불`;
  }
}

export type OwnerPaymentHistoryResult =
  | { status: "loaded"; data: OwnerPaymentHistory }
  | { status: "unauthorized" }
  /** 403 — 사장님이 아님 */
  | { status: "forbidden" }
  | { status: "error" };

/** 결제 내역을 불러온다 */
export async function loadOwnerPaymentHistory(): Promise<OwnerPaymentHistoryResult> {
  try {
    return { status: "loaded", data: await fetchOwnerPayments() };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
    }
    return { status: "error" };
  }
}

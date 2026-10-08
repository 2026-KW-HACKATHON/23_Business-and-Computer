import { ApiError } from "../../../api/client";
import { formatMonthDay, koreaDate } from "../../../lib/date";
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
 * 결제 한 줄의 둘째 줄. 보관 중은 결제한 날, 정산 완료는 정산한 날, 환불은 환불한 날 (모두 한국 날짜).
 * 「김광운 학생 · 9월 20일」(보관 중) · 「… · 9월 12일 정산」(정산 완료) ·
 * 「… · 8월 10일 작업 중 취소 · 64,000원 환불」(부분 환불) · 「… · 8월 10일 · 80,000원 환불」(전액 환불)
 */
export function paymentDetailText(payment: OwnerPaymentItem): string {
  const student = payment.studentName?.trim() ? studentTitle(payment.studentName) : "학생";
  const paidOn = payment.approvedAt ? formatMonthDay(koreaDate(payment.approvedAt)) : undefined;
  const settledOn = payment.settledDate ? formatMonthDay(payment.settledDate) : undefined;
  const refundedOn = payment.refundedDate ? formatMonthDay(payment.refundedDate) : undefined;
  const refund = `${formatWon(payment.refundAmount ?? 0)} 환불`;
  switch (payment.status) {
    case "HELD":
      return [student, paidOn].filter(Boolean).join(" · ");
    case "SETTLED":
      return [student, settledOn && `${settledOn} 정산`].filter(Boolean).join(" · ");
    case "PARTIALLY_REFUNDED":
      return [student, refundedOn ? `${refundedOn} 작업 중 취소` : "작업 중 취소", refund].join(" · ");
    case "FULLY_REFUNDED":
      return [student, refundedOn, refund].filter(Boolean).join(" · ");
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

import { apiData } from "../../../api/client";

/** 결제 한 건의 상태. 보관 중 · 정산 완료 · 부분 환불(작업 중 취소) · 전액 환불(학생의 의뢰서 거절) */
export type PaymentHistoryStatus = "HELD" | "SETTLED" | "PARTIALLY_REFUNDED" | "FULLY_REFUNDED";

/** GET /payments 의 결제 한 건 (의뢰 하나) */
export interface PaymentHistoryItem {
  jobId: number;
  title: string;
  /** 결제한 작업비(원) */
  amount: number;
  /** 돌려받은 금액(원). 환불이 없으면 없음 */
  refundAmount?: number | null;
  /** 결제한 시각 (한국 시각 "2026-10-06T12:00:00+09:00") */
  approvedAt?: string | null;
  studentName?: string | null;
  status: PaymentHistoryStatus;
  /** 정산한 날 (한국 날짜 "2026-10-06"). 정산 완료일 때만 */
  settledDate?: string | null;
  /** 환불한 날 (한국 날짜). 부분 · 전액 환불일 때만 */
  refundedDate?: string | null;
}

/** GET /payments 의 답. 요약과 달마다 묶은 결제 */
export interface PaymentHistoryResponse {
  summary: { thisMonthPaymentAmount: number; heldAmount: number; totalSettledAmount: number };
  months: { yearMonth: string; payments: PaymentHistoryItem[] }[];
}

/** GET /payments — 사장님 결제 내역 */
export async function fetchOwnerPayments(): Promise<PaymentHistoryResponse> {
  const data = await apiData<PaymentHistoryResponse | undefined>("/payments");
  if (!data) throw new Error("Payment history response has no data");
  return data;
}

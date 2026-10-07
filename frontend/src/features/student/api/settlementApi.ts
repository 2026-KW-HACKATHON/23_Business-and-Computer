import { apiData } from "../../../api/client";

/**
 * 정산 한 줄의 상태. SCHEDULED 정산 예정(작업 중) · SETTLED 정산 완료 ·
 * START_COMPENSATION 착수 보상(사장님이 작업 중에 취소) · REFUNDED 작업 시작 전에 의뢰서를 거절해 전액 환불(학생 몫 0원)
 */
export type SettlementStatus = "SCHEDULED" | "SETTLED" | "START_COMPENSATION" | "REFUNDED";

/** 정산 한 줄 (의뢰 하나) */
export interface SettlementItem {
  jobId: number;
  title: string;
  /** 학생이 받는 금액 (착수 보상은 보상금, 환불은 0) */
  amount: number;
  /** 정산 완료는 의뢰 완료일, 착수 보상 · 환불은 환불한 날 (한국 날짜). 정산 예정은 없다 */
  settledDate?: string | null;
  storeName?: string | null;
  status: SettlementStatus;
}

/** GET /settlements 의 답 (SettlementHistoryResponse) */
export interface SettlementHistoryResponse {
  summary: {
    /** 이번 달에 결제된 작업의 금액 합 */
    thisMonthWorkAmount: number;
    scheduledAmount: number;
    /** 지금까지 정산된 금액 합 (착수 보상 포함) */
    totalSettledAmount: number;
  };
  /** 결제한 달(한국 시간)마다, 최근 달부터. yearMonth 는 「2026-09」 */
  months: { yearMonth: string; settlements: SettlementItem[] }[];
}

/** GET /settlements — 학생 본인의 정산 내역. 학생이 아니면 403 */
export async function fetchSettlements(): Promise<SettlementHistoryResponse> {
  const data = await apiData<SettlementHistoryResponse | undefined>("/settlements");
  if (!data) throw new Error("Settlement history response has no data");
  return data;
}

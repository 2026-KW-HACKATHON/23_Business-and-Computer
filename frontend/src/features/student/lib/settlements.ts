import { ApiError } from "../../../api/client";
import { formatMonthDay } from "../../../lib/date";
import { fetchSettlements } from "../api/settlementApi";
import type { SettlementHistoryResponse, SettlementItem, SettlementStatus } from "../api/settlementApi";
import type { SettlementSummary } from "../types";

/** 정산 내역 (GET /settlements): 요약과 달마다 묶은 정산 */
export type SettlementHistory = SettlementHistoryResponse;
export type { SettlementItem, SettlementStatus };

/** 정산 요약 3칸 (이번 달 작업비 · 정산 예정 · 정산 완료) */
export function settlementSummaryOf(history: SettlementHistory): SettlementSummary {
  return {
    thisMonth: history.summary.thisMonthWorkAmount,
    expected: history.summary.scheduledAmount,
    settled: history.summary.totalSettledAmount,
  };
}

/** 상태 칩 글자. 작업 시작 전에 거절한 의뢰(환불)는 「성사되지 않음」 */
export const SETTLEMENT_STATUS_LABEL: Record<SettlementStatus, string> = {
  SCHEDULED: "정산 예정",
  SETTLED: "정산 완료",
  START_COMPENSATION: "착수 보상",
  REFUNDED: "성사되지 않음",
};

/**
 * 정산 한 줄의 둘째 줄. 「치킨플러스 · 작업 중」 · 「공룡카페 · 9월 12일 정산」 ·
 * 「치킨플러스 · 8월 10일 사장님 사정 취소」 · 「광운카페 · 9월 3일 성사되지 않음」
 */
export function settlementDetailText(item: SettlementItem): string {
  const store = item.storeName?.trim() || "가게";
  const day = item.settledDate ? `${formatMonthDay(item.settledDate)} ` : "";
  switch (item.status) {
    case "SCHEDULED":
      return `${store} · 작업 중`;
    case "SETTLED":
      return `${store} · ${day}정산`;
    case "START_COMPENSATION":
      return `${store} · ${day}사장님 사정 취소`;
    case "REFUNDED":
      return `${store} · ${day}성사되지 않음`;
  }
}

export type SettlementHistoryResult =
  | { status: "loaded"; data: SettlementHistory }
  | { status: "unauthorized" }
  /** 403 — 학생이 아님 */
  | { status: "forbidden" }
  | { status: "error" };

/** 정산 내역을 불러온다 */
export async function loadSettlementHistory(): Promise<SettlementHistoryResult> {
  try {
    return { status: "loaded", data: await fetchSettlements() };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
    }
    return { status: "error" };
  }
}

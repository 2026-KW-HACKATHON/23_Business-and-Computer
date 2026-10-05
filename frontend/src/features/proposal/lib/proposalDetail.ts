import { ApiError } from "../../../api/client";
import { formatMonthDay } from "../../../lib/date";
import { fetchProposalDetail } from "../api/proposalDetailApi";
import type { ProposalDetailResponse, ProposalSpecialtyCategory } from "../api/proposalDetailApi";

/** 제안 상세 (GET /proposals/{id}) */
export type ProposalDetail = ProposalDetailResponse;

/** 뱃지로 보일 대분류 이름 (겹치지 않게). 「기타」는 대분류 「기타」 하나라 뱃지도 하나다 */
export function proposalBadgeNames(categories: ProposalSpecialtyCategory[]): string[] {
  return [...new Set(categories.map((category) => category.name))];
}

/**
 * 한국 시각 "2026-10-05T14:03:11" → 「10월 5일」. 브라우저 시간대로 밀리지 않게 날짜 글자를
 * 그대로 읽는다. 값이 없거나 날짜 모양이 아니면 undefined (그 줄을 숨긴다)
 */
export function proposalMonthDay(createdAt: string | null | undefined): string | undefined {
  const date = createdAt?.slice(0, 10);
  if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return undefined;
  return formatMonthDay(date);
}

/** 「초안 2일 · 최종 4일」 (수락된 날부터 걸리는 날) */
export function expectedDaysText(draftDays: number, finalDays: number): string {
  return `초안 ${draftDays}일 · 최종 ${finalDays}일`;
}

/** PENDING 상세: 「수락하면 10월 7일까지 초안, 10월 9일까지 최종」. 서버가 날짜를 안 주면 undefined */
export function estimatedDeadlineText(detail: ProposalDetail): string | undefined {
  const { estimatedDraftDeadline: draft, estimatedFinalDeadline: final } = detail;
  if (!draft || !final) return undefined;
  return `수락하면 ${formatMonthDay(draft)}까지 초안, ${formatMonthDay(final)}까지 최종`;
}

/** GET /proposals/{id} 결과 */
export type ProposalDetailResult =
  | { status: "loaded"; proposal: ProposalDetail }
  /** apiData 가 /refresh 로 한 번 다시 시도한 뒤에도 401 */
  | { status: "unauthorized" }
  /** 404 PROPOSAL_404 (없는 제안 · 다른 데모 세션의 제안) */
  | { status: "notFound" }
  | { status: "error" };

export async function loadProposalDetail(proposalId: number): Promise<ProposalDetailResult> {
  try {
    return { status: "loaded", proposal: await fetchProposalDetail(proposalId) };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 404) return { status: "notFound" };
    }
    return { status: "error" };
  }
}

/** 주소의 제안 id ("12") → 12. 양의 정수가 아니면 undefined (요청하지 않고 「없음」으로 보인다) */
export function parseProposalId(value: string | undefined): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

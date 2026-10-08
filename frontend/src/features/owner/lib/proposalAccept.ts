/** 제안 수락에서 사장님이 정한 값. 카카오페이에 다녀와 「다시 결제하기」로 돌아오면 그대로 채운다 */
export interface ProposalAcceptDraft {
  proposalId: number;
  budget: number;
  revisions: number;
  message: string;
  agreed: boolean;
}

const PROPOSAL_ACCEPT_DRAFT_KEY = "gakkum.proposalAcceptDraft";

/** 결제하러 가기 전에 남긴다. 저장소를 못 쓰면 남기지 않는다 (돌아오면 처음 값) */
export function saveProposalAcceptDraft(draft: ProposalAcceptDraft): void {
  try {
    sessionStorage.setItem(PROPOSAL_ACCEPT_DRAFT_KEY, JSON.stringify(draft));
  } catch {
    // 개인정보 보호 모드 등에서 저장소를 못 쓴다
  }
}

/** 이 제안에 남긴 값. 없거나 다른 제안이면 undefined */
export function readProposalAcceptDraft(proposalId: number): ProposalAcceptDraft | undefined {
  try {
    const raw = sessionStorage.getItem(PROPOSAL_ACCEPT_DRAFT_KEY);
    const value = raw ? (JSON.parse(raw) as Partial<ProposalAcceptDraft>) : undefined;
    if (
      value?.proposalId !== proposalId ||
      !Number.isSafeInteger(value.budget) ||
      !Number.isSafeInteger(value.revisions) ||
      (value.revisions ?? 0) < 1 ||
      typeof value.message !== "string" ||
      typeof value.agreed !== "boolean"
    ) {
      return undefined;
    }
    return value as ProposalAcceptDraft;
  } catch {
    return undefined;
  }
}

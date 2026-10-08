import { SAMPLE_PROPOSAL_EXAMPLES } from "../lib/sampleProposals";
import type { ProposalExample } from "../types";

/** 홈 「이런 제안은 어때요?」 예시 하나. 제안 보내기를 이 내용으로 채워 시작한다 */
export function useProposalExample(exampleId: string | undefined): ProposalExample | undefined {
  return SAMPLE_PROPOSAL_EXAMPLES.find((e) => e.id === exampleId);
}

/** 홈 「이런 제안은 어때요?」 예시 전체 */
export function useProposalExamples(): ProposalExample[] {
  return SAMPLE_PROPOSAL_EXAMPLES;
}

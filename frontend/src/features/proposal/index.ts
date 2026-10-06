/** 제안 상세(GET /proposals/{id}) · 공감의 공개 입구 — 학생 · 사장님 기능이 여기서만 import 한다. */
export type {
  ProposalJobStatus,
  ProposalSpecialtyCategory,
  ProposalStatus,
  ProposalStudentResponse,
} from "./api/proposalDetailApi";
export { useProposalDetail } from "./hooks/useProposalDetail";
export { useProposalLikes } from "./hooks/useProposalLikes";
export type { ProposalLike } from "./hooks/useProposalLikes";
export {
  estimatedDeadlineText,
  expectedDaysText,
  proposalBadgeNames,
  proposalMonthDay,
} from "./lib/proposalDetail";
export type { ProposalDetail } from "./lib/proposalDetail";

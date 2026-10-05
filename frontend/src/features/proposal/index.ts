/** 제안 상세(GET /proposals/{id})의 공개 입구 — 학생 · 사장님 기능이 여기서만 import 한다. */
export type {
  ProposalJobStatus,
  ProposalSpecialtyCategory,
  ProposalStatus,
  ProposalStudentResponse,
} from "./api/proposalDetailApi";
export { useProposalDetail } from "./hooks/useProposalDetail";
export {
  estimatedDeadlineText,
  expectedDaysText,
  proposalBadgeNames,
  proposalMonthDay,
} from "./lib/proposalDetail";
export type { ProposalDetail } from "./lib/proposalDetail";

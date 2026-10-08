import type { Field } from "../../types/field";
import type { ApplicationPlan } from "../../types/workPlan";
import type { ExploreProposalCard } from "../explore";
import type { AppliedJob } from "./lib/appliedJobs";
import type { FinishedJob } from "./lib/finishedJobs";
import type { ProgressJob } from "./lib/progressJobs";
import type { SentProposal } from "./lib/sentProposals";

/** 마감 단계. draft = 초안, final = 최종 */
export type DeadlineStage = "draft" | "final";

/**
 * GET /explore/stores 의 가게 하나 (사장님 프로필 하나 = 가게 하나).
 * 제안을 보낼 때 ownerProfileId 를 쓰고, 가게 하나만 조회하는 API 가 없어 이름·업종·주소도 함께 넘긴다.
 */
export interface ExploreStore {
  ownerProfileId: number;
  name: string;
  /** 백엔드 업종 이름 (예: 음식점) */
  category: string;
  address: string;
}

export interface WorkFile {
  name: string;
  /** 「24.1MB」 */
  size: string;
  /** 제출 화면에서 고른 파일 (올릴 때 쓴다) */
  file?: File;
}

/** 지원하기에서 쓰는 작업계획서 */
export type { ApplicationPlan };

/** 홈 「이런 제안은 어때요?」 예시. 누르면 제안 보내기를 이 내용으로 채워 시작한다 */
export interface ProposalExample {
  id: string;
  field: Field;
  task: string;
  /** 줄바꿈(\n)까지 그대로 */
  title: string;
  /** 제안 보내기 3/4 에 미리 채울 제목 */
  proposalTitle: string;
}

/** 정산 요약 3칸 (GET /settlements 의 summary) */
export interface SettlementSummary {
  /** 이번 달에 결제된 작업의 금액 합 */
  thisMonth: number;
  /** 정산 예정 (작업 중) */
  expected: number;
  /** 지금까지 정산된 금액 (착수 보상 포함) */
  settled: number;
}

/** 확인할 일 카드 */
export type StudentTodo =
  /** 나와 매칭된 진행 중 작업 (GET /me/jobs?status=MATCHED) */
  | { type: "drafting"; job: ProgressJob }
  | { type: "revising"; job: ProgressJob }
  /** 사장님이 결제해 의뢰서가 온 내 제안 (GET /me/proposals 의 AWAITING_START) */
  | { type: "proposalAgreement"; proposal: SentProposal };

/** 기다리는 중 한 줄 */
export type StudentWaitingItem =
  | { type: "proposal"; proposal: SentProposal }
  | { type: "application"; job: AppliedJob };

export interface StudentHome {
  /**
   * 이력(작업 · 지원 · 제안)이 하나도 없으면 할 일 대신 사용법 안내.
   * 작업 · 지원 · 제안이 없고 지원한 의뢰나 보낸 제안을 아직 못 불러왔으면(불러오는 중 · 실패) undefined (모름)
   */
  firstVisit: boolean | undefined;
  todos: StudentTodo[];
  /** 공감 많은 다른 학생 제안 (GET /explore). 불러오는 중 · 실패 · 내 제안 목록을 모를 때는 빈 목록 */
  peerProposals: ExploreProposalCard[];
  /** 사장님이 확인 중 (낸 결과물, GET /me/jobs?status=MATCHED) */
  checking: ProgressJob[];
  /** 진행 중 작업을 불러온 상태와 다시 시도 */
  progress: "loading" | "error" | "loaded";
  reloadProgress: () => void;
  /** 수락 대기 중인 보낸 제안 + 고르는 중인 지원. 보낸 제안은 불러온 뒤에만 들어간다 */
  waiting: StudentWaitingItem[];
  /** 보낸 제안(GET /me/proposals)을 불러온 상태와 다시 시도 */
  sentProposals: "loading" | "error" | "loaded";
  reloadSentProposals: () => void;
  examples: ProposalExample[];
  /** 완료한 작업 (GET /settlements 의 정산 완료). 최근 끝난 것부터 */
  done: FinishedJob[];
}

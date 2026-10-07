import type { Field } from "../../types/field";
import type { StoreCategory } from "../../types/storeCategory";
import type { WorkKind } from "../../types/workKind";
import type { ApplicationPlan, WorkPlanContent } from "../../types/workPlan";
import type { ExploreProposalCard } from "../explore";
import type { AppliedJob } from "./lib/appliedJobs";
import type { FinishedJob } from "./lib/finishedJobs";
import type { ProgressJob } from "./lib/progressJobs";
import type { SentProposal } from "./lib/sentProposals";

/** 마감 단계. draft = 초안, final = 최종 */
export type DeadlineStage = "draft" | "final";

/** 월계1동 가게 (가게 탐색 · 제안 보내기 1/4) */
export interface Store {
  id: string;
  name: string;
  category: StoreCategory;
  address: string;
}

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

/** 다른 데이터에서 가게를 가리킬 때 */
export interface StoreRef {
  id: string;
  name: string;
}

export interface WorkFile {
  name: string;
  /** 「24.1MB」 */
  size: string;
  /** 제출 화면에서 고른 파일 (올릴 때 쓴다) */
  file?: File;
}

/** 작업 기록 한 줄 */
export interface WorkHistoryItem {
  date: string;
  text: string;
}

/** 사장님이 남긴 후기 */
export interface OwnerReview {
  rating: number;
  /** 좋았던 점 칩 */
  points: string[];
  text: string;
  date: string;
}

/**
 * 내 작업 상태.
 * awaitingAgreement = 제안이 수락돼 의뢰서가 왔고 내가 동의하기 전,
 * drafting = 초안 만드는 중, revising = 수정 요청을 받아 수정안 만드는 중,
 * submitted = 초안 · 수정안을 내고 사장님 확인을 기다리는 중
 */
export type StudentWorkStatus =
  | "awaitingAgreement"
  | "drafting"
  | "revising"
  | "submitted"
  | "completed"
  | "canceled";

/** 사장님의 수정 요청 */
export interface RevisionRequest {
  requestedOn: string;
  text: string;
  attachments: string[];
}

/** 내 작업 하나 (의뢰에 선정됐거나 제안이 수락된 뒤). 채팅방도 이 id */
export interface StudentWork {
  id: string;
  /** 의뢰에 뽑혔으면 request, 내 제안이 수락됐으면 proposal */
  kind: WorkKind;
  title: string;
  field: Field;
  tasks: string[];
  store: StoreRef;
  budget: number;
  draftDue: string;
  finalDue: string;
  revisionLimit: number;
  /** 지금까지 받은 수정 요청 수 */
  revisionCount: number;
  /** 내 작업계획서 (의뢰 지원서, 제안으로 시작했으면 제안서의 작업계획서 글) */
  plan: WorkPlanContent;
  planSentOn: string;
  status: StudentWorkStatus;
  /** 제안에서 시작된 작업이면 그 제안 */
  proposalId?: string;
  /** 사장님 의뢰서가 도착한 날 (제안 수락) */
  requestArrivedOn?: string;
  /** 의뢰서에 적힌 사장님의 한마디 */
  ownerMessage?: string;
  /** 작업이 시작된 날 (안전결제가 끝난 날 · 내가 동의한 날) */
  startedOn?: string;
  /** 마지막으로 낸 결과물 (초안 · 수정안) */
  files: WorkFile[];
  /** 결과물과 함께 사장님께 남긴 말 */
  myMessage?: string;
  /** 마지막으로 결과물을 낸 날 (submitted) */
  submittedOn?: string;
  /** 이날까지 사장님 답이 없으면 자동 완료 (submitted) */
  autoCompleteOn?: string;
  /** 마지막 수정 요청 (revising) */
  revisionRequest?: RevisionRequest;
  completedOn?: string;
  completedBy?: "owner" | "auto";
  /** 내 결과물 보기의 최종 수정안 요약 (예: 로고 시안 4장 · 수정 1회 반영) */
  resultSummary?: string;
  review?: OwnerReview;
  history: WorkHistoryItem[];
  /** 사장님이 취소한 작업. reward = 착수 보상 */
  cancel?: {
    canceledOn: string;
    stage: "beforeStart" | "inProgress";
    reason: string;
    message?: string;
    reward: number;
  };
}

/** 지원하기에서 쓰는 작업계획서 */
export type { ApplicationPlan };

/** 내가 보낸 제안. accepted = 사장님이 의뢰서를 보내 작업(workId)이 생김 */
export type MyProposalStatus = "waiting" | "accepted";

export interface MyProposal {
  id: string;
  title: string;
  field: Field;
  /** 제안 보내기 2/4 에서 고른 일 */
  tasks: string[];
  store: StoreRef;
  sentOn: string;
  empathyCount: number;
  status: MyProposalStatus;
  /** 손님 눈으로 본 문제 */
  problem: string;
  /** 이렇게 바꿔 드릴게요 */
  solution: string;
  plan: string;
  wishBudget: number;
  /** 예상 기간 (수락된 날부터) */
  draftDays: number;
  finalDays: number;
  attachments: string[];
  workId?: string;
}

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

export type StudentNotificationType =
  | "SELECTED"
  | "NOT_SELECTED"
  | "PROPOSAL_ACCEPTED"
  | "EMPATHY_GROWN"
  | "REVISION_REQUESTED"
  | "CHAT_MESSAGE"
  | "SETTLED"
  | "DUE_SOON"
  | "REVIEW_RECEIVED"
  | "WORK_CANCELED";

export interface StudentNotification {
  id: string;
  type: StudentNotificationType;
  title: string;
  body: string;
  /** ISO 시각 */
  createdAt: string;
  read: boolean;
  /** 눌렀을 때 갈 작업 · 제안 · 의뢰 id (종류마다 다름) */
  targetId: string;
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

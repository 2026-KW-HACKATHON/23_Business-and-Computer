import type { Field } from "../../types/field";
import type { StoreCategory } from "../../types/storeCategory";
import type { WorkKind } from "../../types/workKind";

/** 마감 단계. draft = 초안, final = 최종 */
export type DeadlineStage = "draft" | "final";

/** 월계1동 가게 (가게 탐색 · 제안 보내기 1/4) */
export interface Store {
  id: string;
  name: string;
  category: StoreCategory;
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
  /** 내 작업계획서. 줄마다 한 문단 */
  plan: string;
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

/** 의뢰 진행. closed = 다른 학생이 뽑혀 모집이 끝남 */
export type RequestProgress = "recruiting" | "closed" | "completed";

/** 가게가 올린 의뢰 (탐색 · 의뢰 상세 · 지원하기) */
export interface StudentRequest {
  id: string;
  title: string;
  field: Field;
  store: StoreRef;
  budget: number;
  draftDue: string;
  finalDue: string;
  revisionLimit: number;
  /** 할 일 칩 */
  tasks: string[];
  /** 맡기고 싶은 일 */
  description: string;
  attachments: string[];
  progress: RequestProgress;
  /** 탐색 최신순 기준 (ISO 시각) */
  createdAt: string;
}

/** 지원하기에서 쓰는 작업계획서 */
export interface ApplicationPlan {
  /** 한 줄 요약 */
  summary: string;
  /** 작업 방법 */
  method: string;
  /** 초안 보내는 날 · 최종본 드리는 날 */
  draftOn: string;
  finalOn: string;
  /** 결과물 */
  deliverable: string;
}

export type ApplicationStatus = "reviewing" | "notSelected";

/** 내가 지원한 의뢰 */
export interface StudentApplication {
  requestId: string;
  appliedOn: string;
  status: ApplicationStatus;
  plan: ApplicationPlan;
}

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
  /** 사장님이 열어 봤는지 */
  seenByOwner: boolean;
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

/** 다른 학생 */
export interface PeerStudent {
  id: string;
  name: string;
  department: string;
  year: string;
  rating?: number;
  completedCount: number;
}

export type PeerProposalProgress = "waitingAcceptance" | "accepted" | "completed";

/** 다른 학생이 보낸 제안 (탐색 · 공감) */
export interface PeerProposal {
  id: string;
  title: string;
  field: Field;
  storeName: string;
  student: PeerStudent;
  receivedOn: string;
  progress: PeerProposalProgress;
  /** 탐색 최신순 기준 (ISO 시각) */
  createdAt: string;
  /** 공감 수 (내 공감 포함) */
  empathyCount: number;
  /** 내가 공감했는지 */
  empathized: boolean;
  /** 내가 보낸 제안 (탐색에도 공개된다). 내 제안에는 공감할 수 없다 */
  mine?: boolean;
  problem: string;
  solution: string;
  attachments: string[];
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

export interface StudentCertificate {
  name: string;
  /** "2025-03" */
  acquiredOn: string;
}

/** 내 프로필. 완료 건수 · 평점 · 후기는 작업에서 센다 */
export interface MyProfile {
  id: string;
  name: string;
  department: string;
  /** 「24학번」 */
  year: string;
  intro: string;
  noShowCount: number;
  badges: string[];
  certificates: StudentCertificate[];
  /** 「behance.net/…」처럼 https:// 없이 */
  portfolioUrl?: string;
}

/** 채팅 메시지. 시각은 ISO */
export type ChatMessage =
  | { id: string; type: "system"; text: string; at: string }
  | { id: string; type: "text"; from: "me" | "partner"; text: string; at: string }
  | { id: string; type: "file"; from: "me" | "partner"; name: string; detail: string; at: string };

export interface StudentChatThread {
  workId: string;
  unreadCount: number;
  messages: ChatMessage[];
}

/** 채팅 목록 한 줄 */
export interface StudentChatRoom {
  workId: string;
  storeName: string;
  workTitle: string;
  status: string;
  lastMessage: string;
  lastMessageAt: string;
  unreadCount: number;
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

/** 정산 내역 한 줄 (작업 하나) */
export type SettlementStatus = "expected" | "settled" | "reward";

export interface StudentSettlement {
  workId: string;
  title: string;
  storeName: string;
  amount: number;
  status: SettlementStatus;
  /** 정산된 날 (expected 면 작업이 시작된 날) */
  date: string;
  autoCompleted?: boolean;
}

export interface SettlementSummary {
  /** 이번 달 = 정산 예정 + 이번 달에 정산된 금액 */
  thisMonth: number;
  expected: number;
  settledThisMonth: number;
}

/** 확인할 일 카드 */
export type StudentTodo =
  | { type: "drafting"; work: StudentWork }
  | { type: "revising"; work: StudentWork }
  | { type: "agreement"; work: StudentWork };

/** 기다리는 중 한 줄 */
export type StudentWaitingItem =
  | { type: "proposal"; proposal: MyProposal }
  | { type: "application"; application: StudentApplication; request: StudentRequest };

export interface StudentHome {
  /** 이력(작업 · 지원 · 제안)이 하나도 없으면 할 일 대신 사용법 안내 */
  firstVisit: boolean;
  todos: StudentTodo[];
  /** 공감을 기다리는 다른 학생 제안 (공감 많은 순) */
  peerProposals: PeerProposal[];
  /** 사장님이 확인 중 (낸 결과물) */
  checking: StudentWork[];
  waiting: StudentWaitingItem[];
  examples: ProposalExample[];
  /** 최근 끝난 것부터 */
  done: StudentWork[];
}

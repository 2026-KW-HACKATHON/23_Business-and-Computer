import type { Field } from "../../types/field";
import type { StoreCategory } from "../../types/storeCategory";
import type { WorkKind } from "../../types/workKind";

/** 마감 단계. draft = 초안, final = 최종 */
export type DeadlineStage = "draft" | "final";

export interface StudentRef {
  name: string;
  /** 있으면 이름 앞에 붙인다 (예: 시각디자인학과) */
  department?: string;
  /** 프로필로 갈 때 쓴다 */
  id?: string;
  /** 「24학번」 */
  year?: string;
}

interface TodoBase {
  /** 버튼을 누르면 가는 작업 · 제안 · 의뢰 id (종류마다 다름) */
  id: string;
  kind: WorkKind;
  title: string;
  field: Field;
}

/** 학생이 초안을 냈다 (id = 작업). 이날까지 확인하지 않으면 자동으로 완료된다 */
export interface DraftArrivedTodo extends TodoBase {
  type: "draftArrived";
  student: StudentRef;
  autoCompleteOn: string;
}

/** 학생 제안이 새로 왔다 (id = 제안) */
export interface ProposalArrivedTodo extends TodoBase {
  type: "proposalArrived";
  student: StudentRef;
  empathyCount: number;
}

/** 의뢰에 학생들이 지원했다 (id = 의뢰) */
export interface ApplicantsTodo extends TodoBase {
  type: "applicants";
  budget: number;
  applicantCount: number;
  draftDue: string;
}

/** 「확인할 일」 카드 한 장. 종류마다 문구와 버튼이 다르다 */
export type OwnerTodo = DraftArrivedTodo | ProposalArrivedTodo | ApplicantsTodo;

/** 「학생이 작업 중」 한 줄 (id = 작업, 채팅방도 이 id) */
export interface OwnerWorkingItem {
  id: string;
  kind: WorkKind;
  title: string;
  student: StudentRef;
  stage: DeadlineStage;
  due: string;
}

export type WaitingStatus = "recruiting";

/** 「기다리는 중」 한 줄 (id = 의뢰). 아직 맡은 학생이 없다 */
export interface OwnerWaitingItem {
  id: string;
  kind: WorkKind;
  title: string;
  stage: DeadlineStage;
  due: string;
  status: WaitingStatus;
}

/** 의뢰 등록 1/3 에서 고른 일 하나 */
export interface PickedTask {
  field: Field;
  task: string;
}

export type { DueDates } from "../../components";

/** 의뢰 등록 2/3 에서 적는 내용 */
export interface RequestContent {
  title: string;
  description: string;
  /** 원. 아직 안 적었으면 0 */
  budget: number;
  /** "2026-09-27". 아직 안 골랐으면 "" */
  draftDue: string;
  finalDue: string;
  /** 최소 1회 */
  revisions: number;
}

/** 「이런 의뢰는 어때요?」 예시. 누르면 의뢰 등록을 이 내용으로 채워 시작한다 */
export interface RequestExample {
  id: string;
  field: Field;
  /** 의뢰 등록 1/3 에서 미리 골라 둘 일 */
  task: string;
  /** 줄바꿈(\n)까지 그대로 보여 준다 */
  title: string;
  /** 의뢰 등록 2/3 에 미리 채워 둘 내용 */
  content: RequestContent;
}

/** 「끝난 일」 한 줄 (id = 작업) */
export interface OwnerDoneItem {
  id: string;
  kind: WorkKind;
  title: string;
  student: StudentRef;
  completedOn: string;
}

/** 사장님 홈 한 화면 분량. 날짜는 모두 YYYY-MM-DD */
export interface OwnerHome {
  /** 백엔드가 알려 주는 첫 활동 여부. 처음이면 할 일 대신 사용법 안내를 보여 준다 */
  firstVisit: boolean;
  hasUnreadNotifications: boolean;
  todos: OwnerTodo[];
  working: OwnerWorkingItem[];
  waiting: OwnerWaitingItem[];
  examples: RequestExample[];
  /** 최근 끝난 것부터 */
  done: OwnerDoneItem[];
}

export type ExploreProgress = "waitingAcceptance" | "accepted" | "completed";

/** 탐색 목록 카드 하나 (다른 가게의 제안·의뢰) */
export interface ExploreItem {
  id: string;
  kind: WorkKind;
  title: string;
  field: Field;
  storeName: string;
  /** 제안만 */
  empathyCount?: number;
  /** 의뢰만: 아직 모집 중이면 마감 (YYYY-MM-DD) */
  deadline?: { stage: DeadlineStage; due: string };
  progress?: ExploreProgress;
  /** 최신순 정렬 기준 (ISO 시각) */
  createdAt: string;
}

/** 채팅 목록의 진행 상태 */
export type ChatProgress =
  | { type: "drafting"; due: string }
  | { type: "revising"; due: string }
  | { type: "draftSubmitted" }
  | { type: "completed" };

/** 채팅방 하나 = 작업 하나 (workId) */
export interface OwnerChatRoom {
  workId: string;
  student: StudentRef;
  workTitle: string;
  progress: ChatProgress;
  lastMessage: string;
  /** ISO 시각 */
  lastMessageAt: string;
  unreadCount: number;
}

/** 알림 종류 (notifications.type) */
export type NotificationType =
  | "DRAFT_SUBMITTED"
  | "REVISION_SUBMITTED"
  | "PROPOSAL_RECEIVED"
  | "APPLICATION_RECEIVED"
  | "CHAT_MESSAGE"
  | "PAYMENT_ESCROWED"
  | "AUTO_COMPLETE_SOON"
  | "REVIEW_REQUEST"
  | "WORK_COMPLETED";

export interface OwnerNotification {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  /** ISO 시각 */
  createdAt: string;
  read: boolean;
  /** 눌렀을 때 갈 작업 · 의뢰 · 제안 id (종류마다 다름) */
  targetId: string;
}

/** 내 정보 화면 머리 */
export interface OwnerProfile {
  storeName: string;
  ownerName: string;
  /** 줄바꿈(\n)은 그대로 보인다 */
  address: string;
  businessVerified: boolean;
  counts: { sent: number; proposals: number; inProgress: number; done: number };
}

export type WorkStatus = "inProgress" | "submitted" | "completed" | "canceled";

export interface WorkFile {
  name: string;
  /** 「24.1MB」 */
  size: string;
}

/** 결과물 보기의 작업 기록 한 줄 */
export interface WorkHistoryItem {
  date: string;
  text: string;
}

/** 작업 하나 (학생을 고르고 결제한 뒤). 작업 확인 · 결과물 · 채팅방 · 작업계획서가 같이 쓴다 */
export interface OwnerWork {
  id: string;
  /** 안전결제한 날 (결제 내역 · 작업 기록과 같다) */
  paidOn: string;
  kind: WorkKind;
  title: string;
  field: Field;
  student: StudentRef;
  budget: number;
  draftDue: string;
  finalDue: string;
  revisionLimit: number;
  revisionCount: number;
  /** 학생 작업계획서. 줄마다 한 문단 */
  plan: string;
  planSentOn: string;
  status: WorkStatus;
  /** 초안 · 수정안이 도착한 날 (submitted) */
  submittedOn?: string;
  /** 이날까지 확인하지 않으면 자동 완료 (submitted) */
  autoCompleteOn?: string;
  completedOn?: string;
  completedBy?: "owner" | "auto";
  files: WorkFile[];
  studentMessage?: string;
  history: WorkHistoryItem[];
  /** 후기를 남겼는지 (completed) */
  reviewed?: boolean;
  /** 취소된 작업 (canceled). stage = 취소한 때, message = 학생에게 남긴 말 */
  cancel?: {
    canceledOn: string;
    refund: number;
    stage: "beforeStart" | "inProgress";
    reason: string;
    message?: string;
  };
}

/** 프로필로 갈 수 있는 학생 */
export interface StudentProfileRef extends StudentRef {
  id: string;
  department: string;
  /** 「21학번」 */
  year: string;
  /** 후기가 없으면 비운다 */
  rating?: number;
  completedCount: number;
}

/** 받은 제안 상세 */
export interface OwnerProposal {
  id: string;
  title: string;
  field: Field;
  receivedOn: string;
  empathyCount: number;
  student: StudentProfileRef;
  /** 손님 눈으로 본 문제 */
  problem: string;
  /** 이렇게 바꿔 드릴게요 */
  solution: string;
  plan: string;
  wishBudget: number;
  expectedDays: number;
  attachments: string[];
}

/** 의뢰에 지원한 학생 */
export interface Applicant {
  student: StudentProfileRef;
  badges: string[];
  plan: string;
}

/** 보낸 의뢰 (모집 중) */
export interface OwnerRequest {
  id: string;
  title: string;
  field: Field;
  budget: number;
  draftDue: string;
  finalDue: string;
  revisionLimit: number;
  /** 할 일 칩 */
  tasks: string[];
  /** 맡기고 싶은 일 */
  description: string;
  attachments: string[];
  applicants: Applicant[];
}

/** 채팅 메시지. 시각은 ISO */
export type ChatMessage =
  | { id: string; type: "system"; text: string; at: string }
  | { id: string; type: "text"; from: "me" | "partner"; text: string; at: string }
  | { id: string; type: "file"; from: "me" | "partner"; name: string; detail: string; at: string };

export interface OwnerChatThread {
  workId: string;
  /** 안 읽은 메시지 수 */
  unreadCount: number;
  messages: ChatMessage[];
}

/** 가게 정보 수정 */
export interface OwnerStore {
  storeName: string;
  category: StoreCategory;
  address: string;
  addressDetail: string;
  phone: string;
  intro: string;
  /** 사업자 정보는 인증된 값이라 바꿀 수 없다 */
  representative: string;
  businessNumber: string;
}

export type PaymentStatus = "escrowed" | "settled" | "partialRefund" | "fullRefund";

/** 결제 내역 한 줄 (작업 하나) */
export interface OwnerPayment {
  id: string;
  workId: string;
  title: string;
  studentName: string;
  amount: number;
  paidOn: string;
  status: PaymentStatus;
  settledOn?: string;
  /** 7일 지나 자동 완료되어 정산됐는지 */
  autoCompleted?: boolean;
  refund?: { on: string; amount: number };
}

export interface PaymentSummary {
  thisMonth: number;
  escrowed: number;
  settled: number;
}

export type PaymentMethod = "kakaoPay" | "card" | "transfer";

/** 안전결제할 의뢰와 고른 학생 */
export interface OwnerCheckout {
  workId: string;
  request: OwnerRequest;
  applicant: Applicant;
}

export interface StudentCertificate {
  name: string;
  /** "2025-03" */
  acquiredOn: string;
}

/** 학생이 받은 사장님 후기 */
export interface StudentReview {
  storeName: string;
  workTitle: string;
  rating: number;
  text: string;
  date: string;
}

/** 학생 프로필 (뱃지 · 자격증 · 후기) */
export interface StudentProfile extends StudentProfileRef {
  /** 한 줄 소개 */
  intro: string;
  proposalCount: number;
  noShowCount: number;
  /** 마감을 지킨 비율 (%). 완료한 작업이 없으면 비운다 */
  onTimeRate?: number;
  badges: string[];
  certificates: StudentCertificate[];
  /** 「notion.so/…」처럼 https:// 없이 */
  portfolioUrl?: string;
  /** 최근 것부터 */
  reviews: StudentReview[];
}

/** 탐색 상세: 다른 가게가 받은 제안 (읽기 전용, 희망 작업비 · 예상 기간은 숨김) */
export interface ExploreProposalDetail {
  id: string;
  kind: "proposal";
  title: string;
  field: Field;
  storeName: string;
  receivedOn: string;
  /** 탐색 카드와 같은 상태 (수락 대기 · 수락됨 · 완료) */
  progress: ExploreProgress;
  /** 탐색 목록 최신순 기준 (ISO 시각) */
  createdAt: string;
  empathyCount: number;
  student: StudentProfileRef;
  problem: string;
  solution: string;
  attachments: string[];
}

/** 탐색 상세: 다른 가게가 올린 의뢰 (읽기 전용, 작업비 · 마감일은 숨김) */
export interface ExploreRequestDetail {
  id: string;
  kind: "request";
  title: string;
  field: Field;
  storeName: string;
  /** 모집 중이면 마감. 끝난 의뢰는 progress = completed */
  deadline?: { stage: DeadlineStage; due: string };
  progress?: ExploreProgress;
  /** 탐색 목록 최신순 기준 (ISO 시각) */
  createdAt: string;
  tasks: string[];
  description: string;
  attachments: string[];
}

export type ExploreDetail = ExploreProposalDetail | ExploreRequestDetail;

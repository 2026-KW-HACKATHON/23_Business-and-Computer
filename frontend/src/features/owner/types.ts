import type { Field } from "../../types/field";
import type { WorkKind } from "../../types/workKind";
import type { ApplicationPlan, WorkPlanContent } from "../../types/workPlan";
import type { OwnerProgressJob } from "./lib/progressJobs";

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
  /** 피그마 분야(Field) 또는 서버 대분류 이름 (받은 제안) */
  field: string;
}

/** 학생이 초안 · 수정안을 냈다 (id = 작업) */
export interface DraftArrivedTodo extends TodoBase {
  type: "draftArrived";
  student: StudentRef;
  /** 수정안이면 true */
  revision?: boolean;
  /** 이날까지 확인하지 않으면 자동으로 완료된다. 모르면 「7일 동안」 */
  autoCompleteOn?: string;
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
  applicantCount: number;
  draftDue: string;
}

/** 「확인할 일」 카드 한 장. 종류마다 문구와 버튼이 다르다 */
export type OwnerTodo = DraftArrivedTodo | ProposalArrivedTodo | ApplicantsTodo;

/** 「학생이 작업 중」 한 줄 (id = 작업). 누르면 지원서 바텀시트, 제안으로 시작했으면 받은 제안 */
export interface OwnerWorkingItem {
  id: string;
  kind: WorkKind;
  title: string;
  student: StudentRef;
  stage: DeadlineStage;
  due: string;
  /** 의뢰에 지원해 맡은 작업. 누르면 이 작업의 지원서를 불러와 바텀시트로 */
  planJob?: OwnerProgressJob;
  proposalId?: string;
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
  /** 참고 사진 (선택, 최대 4장). 등록할 때 올린다 */
  photos: File[];
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

/** 「작업계획서 보기」 바텀시트 내용. 모르는 칸은 빼고 보인다 */
export interface WorkPlanSheetContent {
  title: string;
  studentName?: string;
  /** 지원할 때 보낸 날 */
  sentOn?: string;
  plan: WorkPlanContent;
  budget?: number;
  draftDue: string;
  finalDue: string;
  revisionLimit?: number;
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
  /** 도착한 결과물 → 결정 대기 제안 → 지원자가 생긴 의뢰. 받은 제안은 불러온 뒤에만 들어간다 */
  todos: OwnerTodo[];
  /** 받은 제안(GET /me/received-proposals)을 불러온 상태와 다시 시도 */
  receivedProposals: "loading" | "error" | "loaded";
  reloadReceivedProposals: () => void;
  /** 진행 중 작업(GET /me/jobs?status=MATCHED)을 불러온 상태와 다시 시도 */
  progress: "loading" | "error" | "loaded";
  reloadProgress: () => void;
  working: OwnerWorkingItem[];
  waiting: OwnerWaitingItem[];
  examples: RequestExample[];
  /** 최근 끝난 것부터 */
  done: OwnerDoneItem[];
}

/** 내 정보 화면 머리 */
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
  /** 학생 작업계획서 (의뢰 지원서, 제안으로 시작했으면 제안서의 작업계획서 글) */
  plan: WorkPlanContent;
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

/** 의뢰에 지원한 학생 */
export interface Applicant {
  student: StudentProfileRef;
  badges: string[];
  plan: ApplicationPlan;
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

/** 가게 정보 수정 */
export interface PaymentSummary {
  thisMonth: number;
  escrowed: number;
  settled: number;
}

export type PaymentMethod = "kakaoPay" | "card" | "transfer";

/** 결제 진행: idle = 결제 전, redirecting = 결제 창으로 가는 중, success / failed = 결과 팝업 */
export type PaymentPhase = "idle" | "redirecting" | "success" | "failed";

export interface StudentCertificate {
  name: string;
  /** 취득 연도. 서버도 연도만 둔다 (acquiredYear) */
  acquiredYear: number;
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
  badges: string[];
  certificates: StudentCertificate[];
  /** 「notion.so/…」처럼 https:// 없이 */
  portfolioUrl?: string;
  /** 최근 것부터 */
  reviews: StudentReview[];
}


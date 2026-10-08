import type { Field } from "../../types/field";
import type { WorkKind } from "../../types/workKind";
import type { ApplicationPlan } from "../../types/workPlan";

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

/** 「학생이 작업 중」 한 줄 (id = 작업). 누르면 보낸 의뢰 상세, 제안으로 시작했으면 받은 제안 상세 */
export interface OwnerWorkingItem {
  id: string;
  kind: WorkKind;
  title: string;
  student: StudentRef;
  stage: DeadlineStage;
  due: string;
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
  /**
   * 이력이 하나도 없는 계정이면 true: 모집 중 · 받은 제안 · 진행 중 · 끝난 의뢰를 다 불러왔는데 모두 비었을 때.
   * 하나라도 있거나 불러오지 못했으면 false, 아직 불러오는 중이면 undefined (모름)
   */
  firstVisit: boolean | undefined;
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

/** 결제 내역 요약 3칸 */
export interface PaymentSummary {
  thisMonth: number;
  escrowed: number;
  settled: number;
}

export type PaymentMethod = "kakaoPay" | "card" | "transfer";

/** 결제 진행: idle = 결제 전, redirecting = 결제 창으로 가는 중, success / failed = 결과 팝업 */
export type PaymentPhase = "idle" | "redirecting" | "success" | "failed";

import type { Field } from "../../types/field";
import type { WorkKind } from "../../types/workKind";

/** 마감 단계. draft = 초안, final = 최종 */
export type DeadlineStage = "draft" | "final";

export interface StudentRef {
  name: string;
  /** 있으면 이름 앞에 붙인다 (예: 시각디자인학과) */
  department?: string;
}

interface TodoBase {
  /** 의뢰 · 제안 id. 버튼을 누르면 그 상세로 간다 */
  id: string;
  kind: WorkKind;
  title: string;
  field: Field;
}

/** 학생이 초안을 냈다. 이날까지 확인하지 않으면 자동으로 완료된다 */
export interface DraftArrivedTodo extends TodoBase {
  type: "draftArrived";
  student: StudentRef;
  autoCompleteOn: string;
}

/** 학생 제안이 새로 왔다 */
export interface ProposalArrivedTodo extends TodoBase {
  type: "proposalArrived";
  student: StudentRef;
  empathyCount: number;
}

/** 의뢰에 학생들이 지원했다 */
export interface ApplicantsTodo extends TodoBase {
  type: "applicants";
  budget: number;
  applicantCount: number;
  draftDue: string;
}

/** 「확인할 일」 카드 한 장. 종류마다 문구와 버튼이 다르다 */
export type OwnerTodo = DraftArrivedTodo | ProposalArrivedTodo | ApplicantsTodo;

/** 「학생이 작업 중」 한 줄 */
export interface OwnerWorkingItem {
  id: string;
  kind: WorkKind;
  title: string;
  student: StudentRef;
  stage: DeadlineStage;
  due: string;
  chatId: string;
}

export type WaitingStatus = "recruiting";

/** 「기다리는 중」 한 줄. 아직 맡은 학생이 없다 */
export interface OwnerWaitingItem {
  id: string;
  kind: WorkKind;
  title: string;
  stage: DeadlineStage;
  due: string;
  status: WaitingStatus;
}

/** 「이런 의뢰는 어때요?」 예시. 누르면 의뢰 등록을 이 내용으로 채워 시작한다 */
export interface RequestExample {
  id: string;
  field: Field;
  /** 줄바꿈(\n)까지 그대로 보여 준다 */
  title: string;
}

/** 「끝난 일」 한 줄 */
export interface OwnerDoneItem {
  id: string;
  kind: WorkKind;
  title: string;
  student: StudentRef;
  completedOn: string;
}

/** 사장님 홈 한 화면 분량. 날짜는 모두 YYYY-MM-DD */
export interface OwnerHome {
  hasUnreadNotifications: boolean;
  todos: OwnerTodo[];
  working: OwnerWorkingItem[];
  waiting: OwnerWaitingItem[];
  examples: RequestExample[];
  /** 최근 끝난 것부터 */
  done: OwnerDoneItem[];
}

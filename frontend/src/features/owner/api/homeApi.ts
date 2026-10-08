import { apiData } from "../../../api/client";
import type { WorkKind } from "../../../types/workKind";

/** 확인할 일의 학생 (OwnerHomeResponse.Student). 전공이 없으면 없음 */
export interface OwnerHomeStudentResponse {
  name: string;
  major?: string | null;
}

/**
 * 확인할 일 하나 (OwnerHomeResponse.Todo). type 에 맞는 값만 온다.
 * draftArrived = 결과물이 도착한 진행 중 작업 (jobId, 제안에서 시작했으면 proposalId도),
 * proposalArrived = 결정을 기다리는 받은 제안 (proposalId), applicants = 지원자가 생긴 모집 중 의뢰 (jobId)
 */
export interface OwnerHomeTodoResponse {
  type: "draftArrived" | "proposalArrived" | "applicants";
  kind: WorkKind;
  jobId?: number | null;
  proposalId?: number | null;
  title: string;
  /** 첫 대분류 이름. 없으면 「기타」로 본다 */
  field?: string | null;
  /** draftArrived · proposalArrived 만 */
  student?: OwnerHomeStudentResponse | null;
  /** draftArrived 만. 도착한 것이 수정안이면 true */
  revision?: boolean | null;
  /** draftArrived 만. 이날까지 확인하지 않으면 자동으로 완료된다 (도착한 한국 날짜 + 7일) "2026-10-16" */
  autoCompleteOn?: string | null;
  /** proposalArrived 만 */
  likeCount?: number | null;
  /** applicants 만 */
  applicantCount?: number | null;
  draftDeadline?: string | null;
}

/** 학생이 작업 중 한 줄 (OwnerHomeResponse.Working). stage · due 는 지금 지킬 마감 */
export interface OwnerHomeWorkingResponse {
  jobId: number;
  kind: WorkKind;
  proposalId?: number | null;
  title: string;
  studentName?: string | null;
  stage: "draft" | "final";
  due: string;
}

/** 기다리는 중 한 줄 (OwnerHomeResponse.Waiting): 지원자가 없는 모집 중 의뢰 */
export interface OwnerHomeWaitingResponse {
  jobId: number;
  kind: WorkKind;
  title: string;
  draftDeadline: string;
}

/** 끝난 일 한 줄 (OwnerHomeResponse.Done): 완료한 작업만 */
export interface OwnerHomeDoneResponse {
  jobId: number;
  kind: WorkKind;
  proposalId?: number | null;
  title: string;
  studentName?: string | null;
  completedOn: string;
}

/**
 * GET /me/home 의 사장님 응답 (OwnerHomeResponse). 날짜는 모두 한국 날짜 "YYYY-MM-DD".
 * 섹션이 [] 면 불러왔고 비었음, null 이면 그 섹션만 불러오지 못함 (같은 요청을 다시 보내면 된다).
 * firstVisit 은 이력이 하나도 없으면 true, 이력을 확인하지 못했고 일부가 실패했으면 null
 */
export interface OwnerHomeResponse {
  role: "OWNER";
  firstVisit: boolean | null;
  /** 도착한 결과물(마감 빠른 순) → 결정 대기 제안 → 지원자가 생긴 의뢰(초안 마감 빠른 순) */
  todos: OwnerHomeTodoResponse[] | null;
  /** 결과물을 기다리는 진행 중 작업, 지금 지킬 마감이 빠른 순 */
  working: OwnerHomeWorkingResponse[] | null;
  /** 지원자가 없는 모집 중 의뢰, 초안 마감이 빠른 순 */
  waiting: OwnerHomeWaitingResponse[] | null;
  /** 완료한 작업, 최근 끝난 것부터 */
  done: OwnerHomeDoneResponse[] | null;
}

/** GET /me/home 의 역할. 사장님 화면은 OWNER 만 쓴다 */
interface HomeRoleResponse {
  role?: string;
}

/**
 * GET /me/home — 로그인한 사람의 역할에 맞는 홈 한 번에. 사장님 응답이 아니면 undefined.
 * 가입을 끝내지 않았으면 403 HOME_403
 */
export async function fetchOwnerHome(): Promise<OwnerHomeResponse | undefined> {
  const data = await apiData<(HomeRoleResponse & Partial<OwnerHomeResponse>) | undefined>("/me/home");
  return data?.role === "OWNER" ? (data as OwnerHomeResponse) : undefined;
}

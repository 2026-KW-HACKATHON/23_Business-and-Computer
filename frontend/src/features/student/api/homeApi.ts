import { apiData } from "../../../api/client";

/** 제안에서 시작했으면 proposal, 사장님이 올린 의뢰면 request */
type HomeWorkKind = "proposal" | "request";

/** 지금 지킬 마감. draft = 초안, final = 최종 */
type HomeDeadlineStage = "draft" | "final";

/**
 * 확인할 일 하나. 의뢰서가 온 내 제안(proposalAgreement) → 초안(drafting) · 수정안(revising) 차례인
 * 작업을 마감이 빠른 것부터. stage · due 는 지금 지킬 마감 (drafting 은 초안, revising 은 최종)
 */
export interface StudentHomeTodoResponse {
  type: "proposalAgreement" | "drafting" | "revising";
  kind: HomeWorkKind;
  /** proposalAgreement 는 의뢰서가 아직 없으면 없다 */
  jobId?: number | null;
  /** 제안에서 시작했을 때만 */
  proposalId?: number | null;
  title: string;
  storeName?: string | null;
  /** 겹치지 않는 대분류 이름 */
  categories?: string[] | null;
  stage?: HomeDeadlineStage | null;
  /** "2026-10-12" */
  due?: string | null;
}

/** 사장님이 확인 중 하나. 결과물을 내고 수정 요청을 받지 않은 작업 */
export interface StudentHomeCheckingResponse {
  jobId: number;
  kind: HomeWorkKind;
  proposalId?: number | null;
  title: string;
  storeName?: string | null;
  submissionType: "DRAFT" | "REVISION";
  /** 마지막으로 낸 날 (한국 날짜) "2026-10-07" */
  submittedOn?: string | null;
}

/** 기다리는 중 하나. 수락 대기 중인 보낸 제안(proposal) 또는 고르는 중인 지원(application) */
export interface StudentHomeWaitingResponse {
  type: "proposal" | "application";
  proposalId?: number | null;
  jobId?: number | null;
  jobApplicationId?: number | null;
  title: string;
  storeName?: string | null;
  /** proposal 만 */
  likeCount?: number | null;
  /** application 만 "2026-10-12" */
  draftDeadline?: string | null;
}

/** 다른 학생들의 제안 공감하기 하나. 공감 많은 제안 상위 5개에서 내 제안을 뺀 앞의 2개 */
export interface StudentHomePeerProposalResponse {
  proposalId: number;
  title: string;
  studentName?: string | null;
  storeName?: string | null;
  status?: "PENDING" | "AWAITING_START" | "ACCEPTED" | "REJECTED" | "CANCELLED" | null;
  likeCount: number;
  likedByMe: boolean;
}

/** 끝난 일 하나. 정산까지 끝난(SETTLED) 작업, 최근 끝난 것부터 */
export interface StudentHomeDoneResponse {
  jobId: number;
  kind: HomeWorkKind;
  proposalId?: number | null;
  title: string;
  storeName?: string | null;
  /** 정산된 날 "2026-10-08" */
  completedOn?: string | null;
}

/**
 * GET /me/home 의 학생 답. 섹션이 null 이면 그 섹션만 불러오지 못한 것이고 (같은 요청을 다시 보내 재시도),
 * 빈 목록이면 불러왔는데 없는 것이다.
 */
export interface StudentHomeResponse {
  role: "STUDENT";
  /** 이력(제안 · 지원 · 작업 · 정산)이 하나도 없으면 true. 일부를 못 불러와 알 수 없으면 null */
  firstVisit: boolean | null;
  todos: StudentHomeTodoResponse[] | null;
  checking: StudentHomeCheckingResponse[] | null;
  waiting: StudentHomeWaitingResponse[] | null;
  peerProposals: StudentHomePeerProposalResponse[] | null;
  done: StudentHomeDoneResponse[] | null;
}

/** GET /me/home — 학생 홈 한 화면 분량을 한 번에. 가입을 끝내지 않은 계정은 403 HOME_403 */
export async function fetchStudentHome(): Promise<StudentHomeResponse> {
  return apiData<StudentHomeResponse>("/me/home");
}

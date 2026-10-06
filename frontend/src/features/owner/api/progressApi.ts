import { apiData } from "../../../api/client";
import type { JobSpecialtyCategory } from "./jobApi";

/** 검토를 기다리는 결과물의 종류 (초안 · 수정안) */
export type SubmissionType = "DRAFT" | "REVISION";

/** GET /me/jobs?status=MATCHED (사장님) 의 의뢰 하나. 학생이 맡아 진행 중인 내 의뢰 */
export interface OwnerMatchedJobResponse {
  jobId: number;
  title: string;
  specialtyCategories: JobSpecialtyCategory[];
  /** "2026-10-12" */
  draftDeadline: string;
  finalDeadline: string;
  studentProfileId: number;
  /** 10자리 학번 */
  studentNumber?: string | null;
  major?: string | null;
  /** 검토를 기다리는 결과물의 종류. 없으면 학생이 만드는 중 */
  submissionType?: SubmissionType | null;
  /** 검토를 기다리는 결과물 id. 없으면 학생이 만드는 중 */
  pendingSubmissionId?: number | null;
  /** STARTED = 첫 초안 전, DRAFT = 초안 검토, REVISION = 수정 요청 뒤 */
  progressStage?: string | null;
}

/** GET /me/jobs?status=MATCHED — 학생이 맡아 진행 중인 내 의뢰 (최신순) */
export async function fetchOwnerMatchedJobs(): Promise<OwnerMatchedJobResponse[]> {
  const data = await apiData<{ jobs?: OwnerMatchedJobResponse[] } | undefined>("/me/jobs?status=MATCHED");
  return data?.jobs ?? [];
}

/** GET /me/chat-rooms 의 방 하나 (쓰는 칸만). 결제가 끝난 의뢰마다 하나 있다 */
export interface ChatRoomResponse {
  roomId: string;
  jobId: number;
  /** 사장님에게는 맡은 학생 이름 */
  counterpartName?: string | null;
  /** 작업비(원) */
  budget?: number | null;
  /** 사장님이 정한 수정 횟수 */
  revisionCount?: number | null;
  /** 의뢰에 지원해 맡은 작업이면 지원서 세 칸. 제안으로 시작했으면 없음 */
  applicationSummary?: string | null;
  applicationWorkPlan?: string | null;
  applicationDeliveryMethod?: string | null;
}

/** GET /me/chat-rooms — 내 채팅방 (최근 메시지순) */
export async function fetchMyChatRooms(): Promise<ChatRoomResponse[]> {
  const data = await apiData<{ rooms?: ChatRoomResponse[] } | undefined>("/me/chat-rooms");
  return data?.rooms ?? [];
}

/** GET /jobs/{jobId}/submission 의 답. 도착해 사장님 확인을 기다리는 초안 · 수정안 */
export interface PendingSubmissionResponse {
  submissionId: number;
  title: string;
  studentName: string;
  submissionType: SubmissionType;
  /** 파일 주소. 끝 경로가 학생이 올린 파일 이름 */
  fileUrls: string[];
  message: string;
  /** 초안 0, 수정안은 1부터 */
  revisionNumber: number;
}

/** GET /jobs/{jobId}/submission — 내 의뢰에 도착한 결과물. 없으면 404 JOB_SUBMISSION_404 */
export async function fetchPendingSubmission(jobId: number): Promise<PendingSubmissionResponse> {
  const data = await apiData<PendingSubmissionResponse | undefined>(`/jobs/${jobId}/submission`);
  if (!data) throw new Error("Pending submission response has no data");
  return data;
}

/** POST /jobs/{jobId}/submissions/{submissionId}/complete — 받은 결과물로 작업을 끝낸다. 답에는 데이터가 없다 */
export async function completeSubmission(jobId: number, submissionId: number): Promise<void> {
  await apiData<unknown>(`/jobs/${jobId}/submissions/${submissionId}/complete`, { method: "POST" });
}

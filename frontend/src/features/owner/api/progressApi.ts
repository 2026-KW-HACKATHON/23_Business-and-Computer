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
  /** 맡은 학생 이름 */
  studentName?: string | null;
  /** 맡은 학생이 올린 프로필 사진. 없으면 없음 */
  studentProfileImageUrl?: string | null;
  /** 작업비(원) */
  budget?: number | null;
  /** 사장님이 정한 수정 횟수 */
  revisionCount?: number | null;
  /** 검토를 기다리는 결과물의 수정 번호 (초안 0). 없으면 학생이 만드는 중 */
  revisionNumber?: number | null;
  /** 검토를 기다리는 결과물이 도착한 시각 (한국 시각 +09:00). 없으면 학생이 만드는 중 */
  submittedAt?: string | null;
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
  /** 사장님에게는 맡은 학생이 올린 프로필 사진 */
  counterpartProfileImageUrl?: string | null;
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

/**
 * POST /jobs/{jobId}/submissions/{submissionId}/revision-request — 도착한 결과물에 수정을 요청한다.
 * 고칠 곳(꼭, 500자까지)과 참고 사진 주소(uploadImage(file, "JOB"), 4장까지)를 보낸다. 답에는 데이터가 없다
 */
export async function requestSubmissionRevision(
  jobId: number,
  submissionId: number,
  request: { message: string; referenceImageUrls: string[] },
): Promise<void> {
  await apiData<unknown>(`/jobs/${jobId}/submissions/${submissionId}/revision-request`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** GET /jobs/{jobId}/submissions 의 결과물 하나. 초안 · 수정안과 그 결과물에 보낸 수정 요청 */
export interface JobSubmissionResponse {
  submissionId: number;
  submissionType: SubmissionType;
  /** 초안 0, 수정안은 1부터 */
  revisionNumber: number;
  /** 파일 주소. 끝 경로가 학생이 올린 파일 이름 */
  fileUrls: string[];
  /** 파일마다 주소와 크기(바이트). 크기를 기록하기 전에 낸 파일은 size 가 null */
  files?: { fileUrl: string; size?: number | null }[] | null;
  /** 학생이 남긴 말 */
  message?: string | null;
  reviewStatus: "PENDING" | "REVISION_REQUESTED" | "APPROVED";
  /** 한국 시각 "2026-10-07T15:22:05+09:00" */
  submittedAt: string;
  /** 이 결과물에 보낸 수정 요청. 보내지 않았으면 없음 */
  revisionRequest?: {
    /** 고칠 곳 */
    message?: string | null;
    /** 참고 사진 주소 (4장까지) */
    referenceImageUrls?: string[] | null;
    /** 한국 시각 "2026-10-07T15:22:05+09:00" */
    requestedAt: string;
  } | null;
}

/**
 * GET /jobs/{jobId}/submissions — 내 의뢰의 모든 초안 · 수정안과 각 수정 요청 (작업 상태와 관계없이).
 * 낸 게 없으면 빈 배열
 */
export async function fetchJobSubmissions(jobId: number): Promise<JobSubmissionResponse[]> {
  const data = await apiData<{ submissions?: JobSubmissionResponse[] } | undefined>(`/jobs/${jobId}/submissions`);
  return data?.submissions ?? [];
}

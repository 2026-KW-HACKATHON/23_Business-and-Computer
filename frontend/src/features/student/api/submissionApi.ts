import { apiData } from "../../../api/client";

/** 결과물 파일 종류. 이미지는 10MB, 그 밖의 파일은 50MB 까지 (채팅 첨부와 같은 규칙) */
export type SubmissionFileType = "IMAGE" | "FILE";

export interface SubmissionUploadRequest {
  type: SubmissionFileType;
  fileName: string;
  contentType: string;
  size: number;
}

interface SubmissionUploadResponse {
  uploadUrl: string;
  /** 업로드 URL 서명에 들어간 헤더. PUT 에 그대로 붙여야 한다 */
  uploadHeaders?: Record<string, string> | null;
  uploadUrlExpiresAt: string;
  /** 제출할 때 보내는 파일 주소 */
  fileUrl: string;
}

/**
 * 결과물 파일 하나를 올리고 제출에 쓸 주소를 돌려준다.
 * POST /jobs/{jobId}/submission/uploads 로 S3 업로드 URL 을 받아 파일을 PUT 한다.
 * S3 는 백엔드가 아니라서 apiData 가 아닌 fetch 를 직접 쓴다.
 */
export async function uploadSubmissionFile(
  jobId: number,
  file: File,
  request: SubmissionUploadRequest,
): Promise<string> {
  const upload = await apiData<SubmissionUploadResponse | undefined>(`/jobs/${jobId}/submission/uploads`, {
    method: "POST",
    body: JSON.stringify(request),
  });
  if (!upload) throw new Error("Submission upload was not prepared");

  const put = await fetch(upload.uploadUrl, {
    method: "PUT",
    headers: upload.uploadHeaders ?? undefined,
    body: file,
  });
  if (!put.ok) throw new Error(`Submission upload failed (${put.status})`);
  return upload.fileUrl;
}

export interface SubmissionRequest {
  /** 1~10개, 겹치지 않게 */
  fileUrls: string[];
  /** 비우면 안 된다 (5000자 이하) */
  message: string;
}

/** POST /jobs/{jobId}/submission — 첫 초안 */
export async function submitDraft(jobId: number, request: SubmissionRequest): Promise<void> {
  await apiData<unknown>(`/jobs/${jobId}/submission`, { method: "POST", body: JSON.stringify(request) });
}

/** GET /jobs/{jobId}/submissions/latest 의 답. 내가 마지막으로 낸 초안 · 수정안과 거기에 받은 수정 요청 */
export interface LatestSubmissionResponse {
  submissionId: number;
  submissionType: "DRAFT" | "REVISION";
  /** 초안 0, 수정안은 1부터 */
  revisionNumber: number;
  /** 파일 주소. 끝 경로가 내가 올린 파일 이름 */
  fileUrls: string[];
  /** 제출할 때 남긴 말 */
  message?: string | null;
  /** PENDING = 사장님 확인 중, REVISION_REQUESTED = 수정 요청 받음, APPROVED = 완료 */
  reviewStatus: "PENDING" | "REVISION_REQUESTED" | "APPROVED";
  /** 한국 시각 ("2026-10-07T14:22:05+09:00"). 날짜는 submissionDay 로 읽는다 */
  submittedAt: string;
  /** 이 결과물에 받은 수정 요청. 받지 않았으면 없음 */
  revisionRequest?: {
    /** 고칠 곳. 본문을 받기 전(백엔드 #196 전)에 한 요청이면 없음 */
    message?: string | null;
    /** 참고 사진 주소 (4장까지) */
    referenceImageUrls?: string[] | null;
    /** 한국 시각 (+09:00) */
    requestedAt: string;
  } | null;
}

/**
 * GET /jobs/{jobId}/submissions/latest — 내가 맡은 의뢰에 마지막으로 낸 결과물 (완료 · 취소 뒤에도).
 * 낸 게 없으면 404 JOB_SUBMISSION_404_LATEST, 내 의뢰가 아니면 404 JOB_404, 학생이 아니면 403 JOB_SUBMISSION_403_VIEW
 */
export async function fetchLatestSubmission(jobId: number): Promise<LatestSubmissionResponse> {
  const data = await apiData<LatestSubmissionResponse | undefined>(`/jobs/${jobId}/submissions/latest`);
  if (!data) throw new Error("Latest submission response has no data");
  return data;
}

/**
 * GET /jobs/{jobId}/submissions — 내가 맡은 의뢰에 낸 모든 초안 · 수정안과 각 수정 요청 (작업 상태와 관계없이).
 * 낸 게 없으면 빈 배열
 */
export async function fetchSubmissionHistory(jobId: number): Promise<LatestSubmissionResponse[]> {
  const data = await apiData<{ submissions?: LatestSubmissionResponse[] } | undefined>(`/jobs/${jobId}/submissions`);
  return data?.submissions ?? [];
}

/** POST /jobs/{jobId}/submission/revisions — 수정 요청을 받은 뒤의 수정안 */
export async function submitRevision(jobId: number, request: SubmissionRequest): Promise<void> {
  await apiData<unknown>(`/jobs/${jobId}/submission/revisions`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

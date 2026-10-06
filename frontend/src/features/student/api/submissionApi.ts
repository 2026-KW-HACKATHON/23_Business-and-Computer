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

/** POST /jobs/{jobId}/submission/revisions — 수정 요청을 받은 뒤의 수정안 */
export async function submitRevision(jobId: number, request: SubmissionRequest): Promise<void> {
  await apiData<unknown>(`/jobs/${jobId}/submission/revisions`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

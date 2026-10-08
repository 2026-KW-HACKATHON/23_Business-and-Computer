import { ApiError, apiFile } from "./client";

/** 한 의뢰에서 고른 결과물 파일 (그 의뢰 제출물의 파일 주소) */
export interface SubmissionFiles {
  jobId: number;
  fileUrls: string[];
}

/** ZIP 을 받지 못한 이유 */
export type SubmissionDownloadFailure = "unauthorized" | "busy" | "missing" | "failed";

/**
 * POST /jobs/submissions/download — 여러 의뢰에서 고른 결과물 파일을 ZIP 하나로 받는다
 * (의뢰한 사장님 또는 담당 학생만). 성공 응답은 application/zip 파일이다.
 */
export async function downloadSubmissionZip(
  jobs: SubmissionFiles[],
): Promise<{ status: "downloaded"; zip: Blob } | { status: "failed"; reason: SubmissionDownloadFailure }> {
  try {
    const zip = await apiFile("/jobs/submissions/download", { method: "POST", body: JSON.stringify({ jobs }) });
    return { status: "downloaded", zip };
  } catch (error) {
    if (!(error instanceof ApiError)) return { status: "failed", reason: "failed" };
    if (error.status === 401) return { status: "failed", reason: "unauthorized" };
    // JOB_SUBMISSION_503_DOWNLOAD: 한꺼번에 내려받는 사람이 많다
    if (error.status === 503) return { status: "failed", reason: "busy" };
    // JOB_SUBMISSION_404_FILE · JOB_SUBMISSION_400_FILE_URL: 저장소에 없거나 이 의뢰 파일이 아니다
    if (error.code === "JOB_SUBMISSION_404_FILE" || error.code === "JOB_SUBMISSION_400_FILE_URL") {
      return { status: "failed", reason: "missing" };
    }
    return { status: "failed", reason: "failed" };
  }
}

/** 받은 파일을 기기에 저장한다 (브라우저 다운로드) */
export function saveFile(file: Blob, fileName: string): void {
  const url = URL.createObjectURL(file);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  // 다운로드가 시작된 뒤에 주소를 놓는다
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

import { ApiError } from "../../../api/client";
import { submitDraft, submitRevision, uploadSubmissionFile } from "../api/submissionApi";
import type { SubmissionFileType } from "../api/submissionApi";

/** 결과물은 한 번에 이만큼까지 낸다 */
export const MAX_SUBMISSION_FILES = 10;

const MB = 1024 * 1024;

/** 백엔드가 받는 확장자와 형식 (채팅 첨부와 같은 규칙). 크기는 이미지 10MB, 그 밖의 파일 50MB */
const SUBMISSION_FORMATS: Record<string, { type: SubmissionFileType; contentTypes: string[] }> = {
  jpg: { type: "IMAGE", contentTypes: ["image/jpeg"] },
  jpeg: { type: "IMAGE", contentTypes: ["image/jpeg"] },
  png: { type: "IMAGE", contentTypes: ["image/png"] },
  webp: { type: "IMAGE", contentTypes: ["image/webp"] },
  gif: { type: "IMAGE", contentTypes: ["image/gif"] },
  pdf: { type: "FILE", contentTypes: ["application/pdf"] },
  // Windows 브라우저는 zip 을 application/x-zip-compressed 로 준다
  zip: { type: "FILE", contentTypes: ["application/zip", "application/x-zip-compressed"] },
  doc: { type: "FILE", contentTypes: ["application/msword"] },
  docx: {
    type: "FILE",
    contentTypes: ["application/vnd.openxmlformats-officedocument.wordprocessingml.document"],
  },
  xls: { type: "FILE", contentTypes: ["application/vnd.ms-excel"] },
  xlsx: { type: "FILE", contentTypes: ["application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"] },
  ppt: { type: "FILE", contentTypes: ["application/vnd.ms-powerpoint"] },
  pptx: {
    type: "FILE",
    contentTypes: ["application/vnd.openxmlformats-officedocument.presentationml.presentation"],
  },
};

const MAX_BYTES: Record<SubmissionFileType, number> = { IMAGE: 10 * MB, FILE: 50 * MB };

/** 파일 고르기 창에서 받을 확장자 */
export const SUBMISSION_FILE_ACCEPT = Object.keys(SUBMISSION_FORMATS)
  .map((ext) => `.${ext}`)
  .join(",");

/** 파일 고르기 아래 회색 안내 */
export const SUBMISSION_FILE_HINT = `이미지 10MB · PDF·문서·ZIP 50MB까지, 최대 ${MAX_SUBMISSION_FILES}개`;

function formatOf(file: File) {
  const ext = file.name.includes(".") ? file.name.split(".").pop()!.toLowerCase() : "";
  const format = SUBMISSION_FORMATS[ext];
  if (!format) return undefined;
  // 브라우저가 형식을 비우거나 다르게 주면 확장자의 대표 형식으로 보낸다
  const contentType = format.contentTypes.includes(file.type) ? file.type : format.contentTypes[0];
  return { type: format.type, contentType };
}

/** 올릴 수 있는 파일이면 true (확장자 · 크기) */
export function isSubmittableFile(file: File): boolean {
  const format = formatOf(file);
  return format !== undefined && file.size > 0 && file.size <= MAX_BYTES[format.type];
}

export type SubmissionKind = "draft" | "revision";

export type SubmissionResult =
  | { status: "sent" }
  | { status: "unauthorized" }
  /** 이미 초안을 냈다 (JOB_SUBMISSION_409_DUPLICATE) */
  | { status: "alreadySubmitted" }
  /** 수정 요청이 없어 수정안을 낼 수 없다 (JOB_SUBMISSION_409_REVISION_NOT_REQUESTED) */
  | { status: "notRequested" }
  /** 나와 매칭된 진행 중 작업이 아니다 (취소 · 완료 · 다른 학생, 404 · 403 · 409 STATUS) */
  | { status: "notAvailable" }
  /** 파일을 올리지 못했거나 서버가 파일을 받지 않았다 */
  | { status: "fileFailed" }
  /** 그 밖의 실패 (네트워크 · 5xx) */
  | { status: "retry" };

function resultOf(error: unknown, uploading: boolean): SubmissionResult {
  if (!(error instanceof ApiError)) return uploading ? { status: "fileFailed" } : { status: "retry" };
  if (error.status === 401) return { status: "unauthorized" };
  switch (error.code) {
    case "JOB_SUBMISSION_409_DUPLICATE":
      return { status: "alreadySubmitted" };
    case "JOB_SUBMISSION_409_REVISION_NOT_REQUESTED":
      return { status: "notRequested" };
    case "JOB_SUBMISSION_409_STATUS":
    case "JOB_SUBMISSION_403":
    case "JOB_404":
      return { status: "notAvailable" };
    case "CHAT_UPLOAD_400_TYPE":
    case "CHAT_UPLOAD_400_SIZE":
    case "JOB_SUBMISSION_400_FILE_URL":
    case "JOB_SUBMISSION_409_FILE_NOT_UPLOADED":
      return { status: "fileFailed" };
    default:
      return { status: "retry" };
  }
}

/**
 * 결과물 파일을 하나씩 올린 뒤 초안 · 수정안으로 낸다 (메시지는 앞뒤 공백을 뺀다).
 * 파일은 isSubmittableFile 을 지난 것만 넘긴다.
 */
export async function sendSubmission(
  jobId: number,
  kind: SubmissionKind,
  files: File[],
  message: string,
): Promise<SubmissionResult> {
  const fileUrls: string[] = [];
  try {
    for (const file of files) {
      const format = formatOf(file);
      if (!format) return { status: "fileFailed" };
      fileUrls.push(
        await uploadSubmissionFile(jobId, file, {
          type: format.type,
          fileName: file.name,
          contentType: format.contentType,
          size: file.size,
        }),
      );
    }
  } catch (error) {
    return resultOf(error, true);
  }

  try {
    const request = { fileUrls, message: message.trim() };
    await (kind === "draft" ? submitDraft(jobId, request) : submitRevision(jobId, request));
    return { status: "sent" };
  } catch (error) {
    return resultOf(error, false);
  }
}

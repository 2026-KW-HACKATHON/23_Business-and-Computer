/*
 * 백엔드가 받는 첨부 형식 (학생 결과물 제출 · 채팅 사진 · 파일이 같은 규칙).
 * 확장자와 형식(MIME)이 짝이 맞아야 하고, 크기는 사진 10MB · 그 밖의 파일 50MB 까지다.
 * 아이폰 HEIC 사진 · 한글(.hwp) 문서는 받지 않는다.
 */

/** 사진(IMAGE) · 파일(FILE) */
export type AttachmentType = "IMAGE" | "FILE";

const MB = 1024 * 1024;

const ATTACHMENT_FORMATS: Record<string, { type: AttachmentType; contentTypes: string[] }> = {
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

/** 종류별 최대 크기 (바이트) */
export const ATTACHMENT_MAX_BYTES: Record<AttachmentType, number> = { IMAGE: 10 * MB, FILE: 50 * MB };

/** 파일 고르기 창에서 받을 확장자 (input accept) */
export const ATTACHMENT_ACCEPT = Object.keys(ATTACHMENT_FORMATS)
  .map((ext) => `.${ext}`)
  .join(",");

/** 파일 이름의 확장자 (소문자). 없으면 빈 글자 */
export function extensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf(".");
  return dot < 0 ? "" : fileName.slice(dot + 1).toLowerCase();
}

/**
 * 받는 형식이면 종류와 보낼 형식(MIME). 브라우저가 형식을 비우거나 다르게 주면 확장자의
 * 대표 형식으로 보낸다. 받지 않는 확장자면 undefined
 */
export function attachmentFormatOf(file: File): { type: AttachmentType; contentType: string } | undefined {
  const format = ATTACHMENT_FORMATS[extensionOf(file.name)];
  if (!format) return undefined;
  const contentType = format.contentTypes.includes(file.type) ? file.type : format.contentTypes[0];
  return { type: format.type, contentType };
}

/** 1.8MB · 240KB */
export function fileSizeText(bytes: number): string {
  return bytes >= MB ? `${(bytes / MB).toFixed(1)}MB` : `${Math.max(1, Math.round(bytes / 1024))}KB`;
}

import { apiData } from "./client";

/** 백엔드 ImagePurpose. 프로필 사진 · 가게 사진 · 제안 참고 사진 */
export type ImagePurpose = "PROFILE" | "STORE" | "PROPOSAL";

/** 백엔드 이미지 업로드(MediaService)가 받는 형식과 확장자 짝 */
export const IMAGE_UPLOAD_EXTENSIONS: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};

/** 백엔드 media-image.max-size (10MB) */
export const MAX_IMAGE_UPLOAD_BYTES = 10 * 1024 * 1024;

interface PrepareImageUploadResponse {
  uploadUrl: string;
  /** 업로드 URL 서명에 들어간 헤더. PUT 에 그대로 붙여야 한다 */
  uploadHeaders: Record<string, string>;
  uploadUrlExpiresAt: string;
  /** 업로드가 끝나면 이 주소로 공개된다 */
  imageUrl: string;
}

/**
 * 사진을 올리고 공개 주소를 돌려준다.
 * POST /media/images/uploads 로 S3 업로드 URL 을 받아 파일을 PUT 한다.
 * S3 는 백엔드가 아니라서 apiFetch(쿠키·JSON 헤더)가 아닌 fetch 를 직접 쓴다.
 * 형식·크기가 맞지 않으면 백엔드가 MEDIA_UPLOAD_400_TYPE / _SIZE 로 막는다.
 */
export async function uploadImage(file: File, purpose: ImagePurpose): Promise<string> {
  const upload = await apiData<PrepareImageUploadResponse>("/media/images/uploads", {
    method: "POST",
    body: JSON.stringify({
      purpose,
      // 원래 파일명은 서버에서 쓰지 않고 확장자만 본다. 형식과 짝이 맞는 이름으로 보낸다
      fileName: `${purpose.toLowerCase()}.${IMAGE_UPLOAD_EXTENSIONS[file.type] ?? ""}`,
      contentType: file.type,
      size: file.size,
    }),
  });
  if (!upload) throw new Error("Image upload was not prepared");

  const put = await fetch(upload.uploadUrl, {
    method: "PUT",
    headers: upload.uploadHeaders,
    body: file,
  });
  if (!put.ok) throw new Error(`Image upload failed (${put.status})`);
  return upload.imageUrl;
}

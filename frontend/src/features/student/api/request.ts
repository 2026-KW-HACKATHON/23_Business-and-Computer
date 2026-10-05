import { apiFetch } from "../../../api/client";
import type { ApiResponse } from "../../../api/client";
import { getAccessToken } from "../../auth";

/*
 * 학생 기능의 백엔드 호출이 쓰는 임시 함수 두 개.
 * TODO: 팀장님 공용 API 가 머지되면 아래 두 함수 몸통을 한 줄씩 바꾼다 (ADR 0020).
 *   - requestData       → `return apiData<T>(path, init);`            (src/api/client.ts)
 *   - uploadImageAsProposal → `return uploadImage(file, "PROPOSAL");` (src/api/media.ts)
 * 그때까지는 가입(signup)과 같은 방식(getAccessToken + apiFetch)을 쓴다.
 * 공용 apiData 는 401 에 /refresh 재시도까지 해 주지만, 지금은 401 을 그대로 던진다.
 */

/** 저장된 access token 을 붙여 보내고 응답의 `data` 만 꺼낸다. 실패는 ApiError 로 던진다 */
export async function requestData<T>(path: string, init: RequestInit = {}): Promise<T | undefined> {
  const token = getAccessToken();
  const response = await apiFetch<ApiResponse<T>>(path, {
    ...init,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  return response.data;
}

interface PrepareImageUploadResponse {
  uploadUrl: string;
  /** 업로드 URL 서명에 들어간 헤더. PUT 에 그대로 붙여야 한다 */
  uploadHeaders: Record<string, string>;
  uploadUrlExpiresAt: string;
  /** 업로드가 끝나면 이 주소로 공개된다 */
  imageUrl: string;
}

/** 백엔드 이미지 업로드(MediaService)가 받는 형식과 확장자 짝 */
const IMAGE_EXTENSIONS: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};

/**
 * 제안 참고 사진을 올리고 공개 주소를 돌려준다. 가입의 프로필 사진 업로드와 같은 흐름으로
 * POST /media/images/uploads (purpose PROPOSAL) → S3 PUT. S3 는 백엔드가 아니라서 fetch 를 직접 쓴다.
 */
export async function uploadImageAsProposal(file: File): Promise<string> {
  const upload = await requestData<PrepareImageUploadResponse>("/media/images/uploads", {
    method: "POST",
    body: JSON.stringify({
      purpose: "PROPOSAL",
      // 원래 파일명은 서버에서 쓰지 않고 확장자만 본다. 형식과 짝이 맞는 이름으로 보낸다
      fileName: `proposal.${IMAGE_EXTENSIONS[file.type] ?? ""}`,
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

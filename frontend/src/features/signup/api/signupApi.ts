import { apiFetch } from "../../../api/client";
import type { ApiResponse } from "../../../api/client";
import { getAccessToken } from "../../auth";
import { PROFILE_PHOTO_EXTENSIONS } from "../lib/profilePhoto";
import type { BusinessInfo, SpecialtyCategory, StudentRegistrationRequest } from "../types";

interface OwnerBusinessVerificationResponse {
  verified: boolean;
}

interface PrepareImageUploadResponse {
  uploadUrl: string;
  /** 업로드 URL 서명에 들어간 헤더. PUT 에 그대로 붙여야 한다 */
  uploadHeaders: Record<string, string>;
  uploadUrlExpiresAt: string;
  /** 업로드가 끝나면 이 주소로 공개된다 */
  imageUrl: string;
}

interface RegistrationResponse {
  accessToken: string;
}

/** 가입 대기(PENDING) 토큰. 회원가입 API 는 모두 이 토큰이 필요하다 */
function authHeaders(): Record<string, string> {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** YYYYMMDD → YYYY-MM-DD (백엔드 openedAt 은 LocalDate ISO 형식만 받는다) */
function toIsoDate(yyyymmdd: string): string {
  return `${yyyymmdd.slice(0, 4)}-${yyyymmdd.slice(4, 6)}-${yyyymmdd.slice(6, 8)}`;
}

/**
 * POST /auth/owner-verification/business — 국세청 진위 확인.
 * 정보가 틀리면 200 에 verified:false, 그 밖의 실패는 ApiError 로 던진다.
 */
export async function verifyOwnerBusiness(info: BusinessInfo): Promise<boolean> {
  const response = await apiFetch<ApiResponse<OwnerBusinessVerificationResponse>>(
    "/auth/owner-verification/business",
    {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({
        representativeName: info.representative.trim(),
        openedAt: toIsoDate(info.openedAt),
        businessNumber: info.number,
      }),
    },
  );
  return response.data?.verified === true;
}

/** POST /auth/student-verification/email — 학교 메일로 인증번호(10분 유효)를 보낸다 */
export async function sendStudentEmailCode(email: string): Promise<void> {
  await apiFetch<ApiResponse<void>>("/auth/student-verification/email", {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ email }),
  });
}

/** POST /auth/student-verification/email/verify — 성공하면 30분 안에 가입 저장을 해야 한다 */
export async function verifyStudentEmailCode(email: string, code: string): Promise<void> {
  await apiFetch<ApiResponse<void>>("/auth/student-verification/email/verify", {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ email, code }),
  });
}

/** GET /specialties — 대분류별 특기 목록 (data 는 배열) */
export async function fetchSpecialties(): Promise<SpecialtyCategory[]> {
  const response = await apiFetch<ApiResponse<SpecialtyCategory[]>>("/specialties", {
    headers: authHeaders(),
  });
  return response.data ?? [];
}

/**
 * 프로필 사진을 올리고 공개 주소를 돌려준다.
 * POST /media/images/uploads 로 S3 업로드 URL 을 받아 파일을 PUT 한다.
 * S3 는 백엔드가 아니라서 apiFetch(쿠키·JSON 헤더)가 아닌 fetch 를 직접 쓴다.
 */
export async function uploadProfileImage(file: File): Promise<string> {
  const response = await apiFetch<ApiResponse<PrepareImageUploadResponse>>("/media/images/uploads", {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({
      purpose: "PROFILE",
      // 원래 파일명은 서버에서 쓰지 않고 확장자만 본다. 형식과 짝이 맞는 이름으로 보낸다
      fileName: `profile.${PROFILE_PHOTO_EXTENSIONS[file.type] ?? ""}`,
      contentType: file.type,
      size: file.size,
    }),
  });
  const upload = response.data;
  if (!upload) throw new Error("Image upload was not prepared");

  const put = await fetch(upload.uploadUrl, {
    method: "PUT",
    headers: upload.uploadHeaders,
    body: file,
  });
  if (!put.ok) throw new Error(`Image upload failed (${put.status})`);
  return upload.imageUrl;
}

/**
 * POST /auth/student — 학생 가입 저장. 역할이 STUDENT 로 바뀐 새 access token 을 돌려준다.
 * refresh token 은 HTTP-only 쿠키로만 온다.
 */
export async function registerStudent(request: StudentRegistrationRequest): Promise<string> {
  const response = await apiFetch<ApiResponse<RegistrationResponse>>("/auth/student", {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify(request),
  });
  const accessToken = response.data?.accessToken;
  if (!accessToken) throw new Error("Signup response has no access token");
  return accessToken;
}

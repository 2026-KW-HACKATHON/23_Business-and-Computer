import { apiData } from "../../../api/client";
import { uploadImage } from "../../../api/media";
import type {
  BusinessCategory,
  BusinessInfo,
  OwnerRegistrationRequest,
  StudentRegistrationRequest,
} from "../types";

interface OwnerBusinessVerificationResponse {
  verified: boolean;
}

interface RegistrationResponse {
  accessToken: string;
}

/* 회원가입 API 는 모두 가입 대기(PENDING) 토큰이 필요하다. apiData 가 붙인다 */

/** YYYYMMDD → YYYY-MM-DD (백엔드 openedAt 은 LocalDate ISO 형식만 받는다) */
function toIsoDate(yyyymmdd: string): string {
  return `${yyyymmdd.slice(0, 4)}-${yyyymmdd.slice(4, 6)}-${yyyymmdd.slice(6, 8)}`;
}

/**
 * POST /auth/owner-verification/business — 국세청 진위 확인.
 * 정보가 틀리면 200 에 verified:false, 그 밖의 실패는 ApiError 로 던진다.
 */
export async function verifyOwnerBusiness(info: BusinessInfo): Promise<boolean> {
  const response = await apiData<OwnerBusinessVerificationResponse | undefined>(
    "/auth/owner-verification/business",
    {
      method: "POST",
      body: JSON.stringify({
        representativeName: info.representative.trim(),
        openedAt: toIsoDate(info.openedAt),
        businessNumber: info.number,
      }),
    },
  );
  return response?.verified === true;
}

/** POST /auth/student-verification/email — 학교 메일로 인증번호(10분 유효)를 보낸다 */
export async function sendStudentEmailCode(email: string): Promise<void> {
  await apiData<void>("/auth/student-verification/email", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

/** POST /auth/student-verification/email/verify — 성공하면 30분 안에 가입 저장을 해야 한다 */
export async function verifyStudentEmailCode(email: string, code: string): Promise<void> {
  await apiData<void>("/auth/student-verification/email/verify", {
    method: "POST",
    body: JSON.stringify({ email, code }),
  });
}

/* GET /specialties 는 가입·제안이 함께 쓰는 features/specialty 의 fetchSpecialties 로 옮겼다 */

/** 프로필 사진을 올리고 공개 주소를 돌려준다 (공용 uploadImage, 용도 PROFILE) */
export function uploadProfileImage(file: File): Promise<string> {
  return uploadImage(file, "PROFILE");
}

/**
 * POST /auth/student — 학생 가입 저장. 역할이 STUDENT 로 바뀐 새 access token 을 돌려준다.
 * refresh token 은 HTTP-only 쿠키로만 온다.
 */
export async function registerStudent(request: StudentRegistrationRequest): Promise<string> {
  const response = await apiData<RegistrationResponse | undefined>("/auth/student", {
    method: "POST",
    body: JSON.stringify(request),
  });
  const accessToken = response?.accessToken;
  if (!accessToken) throw new Error("Signup response has no access token");
  return accessToken;
}

/** GET /business-categories — 가게 업종 목록 (data 는 배열) */
export async function fetchBusinessCategories(): Promise<BusinessCategory[]> {
  const categories = await apiData<BusinessCategory[] | undefined>("/business-categories");
  return categories ?? [];
}

/** 매장 대표사진을 올리고 공개 주소를 돌려준다 (공용 uploadImage, 용도 STORE) */
export function uploadStoreImage(file: File): Promise<string> {
  return uploadImage(file, "STORE");
}

/**
 * POST /auth/owner — 사장님 가입 저장. 역할이 OWNER 로 바뀐 새 access token 을 돌려준다.
 * refresh token 은 HTTP-only 쿠키로만 온다.
 */
export async function registerOwner(request: OwnerRegistrationRequest): Promise<string> {
  const response = await apiData<RegistrationResponse | undefined>("/auth/owner", {
    method: "POST",
    body: JSON.stringify(request),
  });
  const accessToken = response?.accessToken;
  if (!accessToken) throw new Error("Signup response has no access token");
  return accessToken;
}

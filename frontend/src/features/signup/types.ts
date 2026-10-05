import type { StoreCategory } from "../../types/storeCategory";

/**
 * 사업자 인증 결과. idle = 아직 인증 전,
 * error = 서버·네트워크 오류 (입력은 그대로 두고 다시 시도).
 * 요청 중 여부는 단계 사이에 남으면 안 되므로 여기 두지 않고 2/3 화면 state 로 둔다.
 */
export type BusinessCheck = "idle" | "verified" | "mismatch" | "error";

/** 사업자 인증 요청 결과. unauthorized·alreadyRegistered 는 화면을 떠난다 */
export type BusinessCheckResult =
  | "verified"
  | "mismatch"
  | "error"
  | "unauthorized"
  | "alreadyRegistered";

export interface BusinessInfo {
  /** 입력 그대로 (예: 123-45-67890) */
  number: string;
  /** YYYYMMDD 8자리 */
  openedAt: string;
  representative: string;
  check: BusinessCheck;
}

/** 사장님 가입 3단계에서 모으는 값. 가입이 끝나거나 화면을 떠나면 버린다 */
export interface OwnerSignupDraft {
  name: string;
  category: StoreCategory | null;
  storeName: string;
  storeAddress: string;
  agreedToTerms: boolean;
  business: BusinessInfo;
  description: string;
  profilePhoto: File | null;
  storePhotos: File[];
  completed: boolean;
}

export const EMPTY_OWNER_SIGNUP: OwnerSignupDraft = {
  name: "",
  category: null,
  storeName: "",
  storeAddress: "",
  agreedToTerms: false,
  business: { number: "", openedAt: "", representative: "", check: "idle" },
  description: "",
  profilePhoto: null,
  storePhotos: [],
  completed: false,
};

/** 입력 그대로 둔다. 보낼 때 다듬고, 완전히 빈 줄은 보내지 않는다 */
export interface Certificate {
  name: string;
  /** 취득 연도 숫자 4자리 (1900~올해) */
  acquiredYear: string;
}

export const EMPTY_CERTIFICATE: Certificate = { name: "", acquiredYear: "" };

/** 자격증 한 줄의 상태. empty 는 보내지 않고, incomplete·invalidYear 는 가입을 막는다 */
export type CertificateStatus = "empty" | "complete" | "incomplete" | "invalidYear";

/** 학생 가입 3단계에서 모으는 값. 가입이 끝나거나 화면을 떠나면 버린다 */
export interface StudentSignupDraft {
  name: string;
  department: string;
  /** 숫자 10자리 */
  studentNumber: string;
  /** 가입 요청이 이미 가입된 학번이라고 답한 번호. 학번을 고치면 오류가 사라진다 */
  takenStudentNumber: string;
  agreedToTerms: boolean;
  /** 인증을 마친 학교 메일. 인증 전이나 인증번호를 다시 보낸 뒤에는 빈 값 */
  verifiedEmail: string;
  intro: string;
  portfolioUrl: string;
  /** 고른 특기 id (GET /specialties). 1~5개 */
  specialtyIds: number[];
  certificates: Certificate[];
  profilePhoto: File | null;
  completed: boolean;
}

export const EMPTY_STUDENT_SIGNUP: StudentSignupDraft = {
  name: "",
  department: "",
  studentNumber: "",
  takenStudentNumber: "",
  agreedToTerms: false,
  verifiedEmail: "",
  intro: "",
  portfolioUrl: "",
  specialtyIds: [],
  certificates: [EMPTY_CERTIFICATE],
  profilePhoto: null,
  completed: false,
};

/** GET /specialties 의 대분류 하나. 백엔드가 id 순서로 준다 */
export interface SpecialtyCategory {
  id: number;
  name: string;
  specialties: { id: number; name: string }[];
}

/** 인증번호 발송 결과. unauthorized·alreadyRegistered 는 화면을 떠난다 */
export type EmailCodeSendResult =
  | "sent"
  | "cooldown"
  | "invalidEmail"
  | "emailTaken"
  | "deliveryFailed"
  | "unauthorized"
  | "alreadyRegistered"
  | "error";

/** 인증번호 확인 결과. invalid = 틀림·만료·5번 넘게 틀림 (서버는 구분하지 않는다) */
export type EmailCodeVerifyResult =
  | "verified"
  | "invalid"
  | "unauthorized"
  | "alreadyRegistered"
  | "error";

/** 3/3 가입 저장이 메일 문제로 실패해 2/3 으로 돌려보낼 때 라우터 state 로 넘기는 값 */
export interface StudentVerifyReturnState {
  notice: "reverify" | "emailTaken";
}

/** 프로필 사진 업로드 결과. uploaded 면 imageUrl 을 가입 요청에 넣는다 */
export type PhotoUploadResult =
  | { status: "uploaded"; imageUrl: string }
  | { status: "unauthorized" }
  | { status: "failed" };

/** 학생 가입 저장 결과. registered 면 새 access token 을 이미 저장했다 */
export type StudentRegisterResult =
  | "registered"
  | "studentNumberTaken"
  | "reverify"
  | "emailTaken"
  | "specialtyInvalid"
  | "unauthorized"
  | "alreadyRegistered"
  | "error";

/** POST /auth/student 요청 본문 (StudentRegistrationRequest) */
export interface StudentRegistrationRequest {
  name: string;
  email: string;
  university: string;
  studentNumber: string;
  major: string;
  portfolioUrl: string;
  introduction: string;
  profileImageUrl: string;
  specialtyIds: number[];
  certificates: { certificateName: string; acquiredYear: number }[];
}

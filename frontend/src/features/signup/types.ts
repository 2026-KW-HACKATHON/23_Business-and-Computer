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

/** 자격증 한 줄의 상태. empty 는 보내지 않고, incomplete·invalidYear·duplicate 는 가입을 막는다 */
export type CertificateStatus = "empty" | "complete" | "incomplete" | "invalidYear" | "duplicate";

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
  /** verifiedEmail 을 인증한 시각 (ms). 30분 안이면 2/3 에서 다시 인증하지 않는다 */
  verifiedAt: number;
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
  verifiedAt: 0,
  intro: "",
  portfolioUrl: "",
  specialtyIds: [],
  certificates: [EMPTY_CERTIFICATE],
  profilePhoto: null,
  completed: false,
};

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
  /** 그 밖의 400 (COMMON_400 등). 같은 값으로 다시 보내도 실패한다 */
  | "invalidInput"
  /**
   * 그 밖의 409 (COMMON_409 = DB 제약 충돌). 입력과 상관없는 서버 데이터 문제일 수 있어
   * 다시 시도하면 될 수도 있다 (docs/failures/0002). 원인이 분명한 409 는 위 값으로 나뉜다
   */
  | "dataConflict"
  /** 5xx · 네트워크. 잠시 후 다시 보내면 될 수 있다 */
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

/** GET /business-categories 한 줄. name 은 가입 1/3 업종 칩 이름과 같다 */
export interface BusinessCategory {
  id: number;
  name: string;
}

/** 고른 업종의 서버 id 찾기 결과 */
export type BusinessCategoryLookup =
  | { status: "found"; id: number }
  /** 목록에 같은 이름이 없다 */
  | { status: "missing" }
  | { status: "unauthorized" }
  | { status: "failed" };

/** POST /auth/owner 요청 본문 (OwnerRegistrationRequest). 빈 선택 값은 빼고 보낸다 */
export interface OwnerRegistrationRequest {
  name: string;
  storeName: string;
  storeAddress?: string;
  categoryId: number;
  /** 입력 그대로 (하이픈 있어도 됨) */
  businessNumber: string;
  /** YYYY-MM-DD */
  openedAt: string;
  representativeName: string;
  description?: string;
  /** 최대 5장 */
  storeImageUrls: string[];
  profileImageUrl?: string;
}

/** 사장님 가입 저장 결과. registered 면 새 access token 을 이미 저장했다 */
export type OwnerRegisterResult =
  | "registered"
  | "unauthorized"
  | "alreadyRegistered"
  /** OWNER_409_BUSINESS_NUMBER: 다른 계정이 이미 쓰는 사업자등록번호 */
  | "businessNumberTaken"
  /** OWNER_400_BUSINESS_NOT_VERIFIED: 가입 저장 때 국세청이 사업자 정보를 확인하지 못했다 */
  | "businessNotVerified"
  /** CATEGORY_400: 보낸 업종 id 가 서버에 없다 */
  | "categoryInvalid"
  /** 그 밖의 400. 같은 값으로 다시 보내도 실패한다 */
  | "invalidInput"
  /** 그 밖의 409 (COMMON_409). 다시 시도하면 될 수도 있다 (docs/failures/0002) */
  | "dataConflict"
  /** 5xx · 네트워크 */
  | "error";

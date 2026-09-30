import type { StoreCategory } from "../../types/storeCategory";

/** 사업자 인증 결과. idle = 아직 인증 전 */
export type BusinessCheck = "idle" | "verified" | "mismatch";

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

export interface Certificate {
  name: string;
  /** 예: 2024.03 */
  acquiredAt: string;
}

/** 학생 가입 3단계에서 모으는 값. 가입이 끝나거나 화면을 떠나면 버린다 */
export interface StudentSignupDraft {
  name: string;
  department: string;
  studentNumber: string;
  agreedToTerms: boolean;
  /** 인증을 마친 학교 메일. 인증 전에는 빈 값 */
  verifiedEmail: string;
  intro: string;
  portfolioUrl: string;
  badges: string[];
  certificates: Certificate[];
  profilePhoto: File | null;
  completed: boolean;
}

export const EMPTY_STUDENT_SIGNUP: StudentSignupDraft = {
  name: "",
  department: "",
  studentNumber: "",
  agreedToTerms: false,
  verifiedEmail: "",
  intro: "",
  portfolioUrl: "",
  badges: [],
  certificates: [{ name: "", acquiredAt: "" }],
  profilePhoto: null,
  completed: false,
};

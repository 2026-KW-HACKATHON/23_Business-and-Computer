import type { StoreCategory } from "../../types/storeCategory";

/** 사업자 인증 결과. idle = 아직 인증 전 */
export type BusinessCheck = "idle" | "verified" | "mismatch" | "closed";

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

import { ApiError } from "../../../api/client";
import { verifyOwnerBusiness } from "../api/signupApi";
import type { BusinessCheckResult, BusinessInfo, OwnerSignupDraft } from "../types";

/** 숫자만 남겨 3-2-5 자리로 하이픈을 넣는다 (최대 10자리) */
export function formatBusinessNumber(input: string): string {
  const digits = input.replace(/\D/g, "").slice(0, 10);
  if (digits.length <= 3) return digits;
  if (digits.length <= 5) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}`;
}

/** 백엔드와 같은 형식: 숫자 10자리, 하이픈은 있어도 없어도 된다 */
export function isBusinessNumber(value: string): boolean {
  return /^\d{3}-?\d{2}-?\d{5}$/.test(value);
}

/** YYYYMMDD 가 실제 있는 날짜이고 오늘보다 뒤가 아니면 true */
export function isOpenedAt(value: string, today: Date = new Date()): boolean {
  if (!/^\d{8}$/.test(value)) return false;
  const year = Number(value.slice(0, 4));
  const month = Number(value.slice(4, 6));
  const day = Number(value.slice(6, 8));
  const date = new Date(year, month - 1, day);
  const isRealDate =
    date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
  return isRealDate && date <= today;
}

/**
 * 사업자 인증. 형식이 틀리면 요청 없이 정보 불일치로 끝내고,
 * 맞으면 POST /auth/owner-verification/business 로 국세청 진위 확인을 한다.
 */
export async function checkBusinessInfo(info: BusinessInfo): Promise<BusinessCheckResult> {
  const wellFormed =
    isBusinessNumber(info.number) && isOpenedAt(info.openedAt) && info.representative.trim() !== "";
  if (!wellFormed) return "mismatch";

  try {
    return (await verifyOwnerBusiness(info)) ? "verified" : "mismatch";
  } catch (error) {
    if (error instanceof ApiError) {
      // 사전 검증을 통과했는데도 400 이면 (서버 날짜 기준 미래 등) 불일치로 본다
      if (error.status === 400) return "mismatch";
      if (error.status === 401) return "unauthorized";
      if (error.status === 409) return "alreadyRegistered";
    }
    // 503 (국세청 응답 실패) · 500 · 네트워크 오류
    return "error";
  }
}

/** 1/3 에서 「다음」을 누를 수 있는지 */
export function isStoreInfoComplete(draft: OwnerSignupDraft): boolean {
  return (
    draft.name.trim() !== "" &&
    draft.category !== null &&
    draft.storeName.trim() !== "" &&
    draft.storeAddress.trim() !== "" &&
    draft.agreedToTerms
  );
}

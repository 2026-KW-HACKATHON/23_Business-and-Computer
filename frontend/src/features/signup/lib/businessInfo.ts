import type { BusinessCheck, BusinessInfo, OwnerSignupDraft } from "../types";

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
 * 사업자 인증 (해커톤 MOCK). 형식만 맞으면 인증 완료, 아니면 정보 불일치.
 * 백엔드 연동 때 POST /auth/owner-verification/business 로 바꾼다.
 */
export function checkBusinessInfo(info: BusinessInfo): BusinessCheck {
  const ok =
    isBusinessNumber(info.number) && isOpenedAt(info.openedAt) && info.representative.trim() !== "";
  return ok ? "verified" : "mismatch";
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

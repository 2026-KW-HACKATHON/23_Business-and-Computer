import type { StudentSignupDraft } from "../types";

/** 광운대 학교 메일만 가입할 수 있다 */
export const SCHOOL_EMAIL_DOMAIN = "@kw.ac.kr";

/** 인증번호 유효 시간 (5분) */
export const VERIFICATION_CODE_TTL_MS = 5 * 60 * 1000;

/**
 * 해커톤 MOCK 인증번호. 백엔드 연동 전에는 메일을 보내지 않고 이 번호만 맞는 것으로 본다.
 * 연동 때 POST /auth/student-verification/email(·/verify)로 바꾼다.
 */
export const MOCK_VERIFICATION_CODE = "123456";

export function isSchoolEmail(email: string): boolean {
  const value = email.trim().toLowerCase();
  return value.endsWith(SCHOOL_EMAIL_DOMAIN) && value.length > SCHOOL_EMAIL_DOMAIN.length;
}

/** 1/3 에서 「다음」을 누를 수 있는지 */
export function isStudentInfoComplete(draft: StudentSignupDraft): boolean {
  return (
    draft.name.trim() !== "" &&
    draft.department.trim() !== "" &&
    draft.studentNumber.trim() !== "" &&
    draft.agreedToTerms
  );
}

/** 남은 시간(ms)을 mm:ss 로 */
export function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const minutes = String(Math.floor(total / 60)).padStart(2, "0");
  const seconds = String(total % 60).padStart(2, "0");
  return `${minutes}:${seconds}`;
}

/** 회원가입 기능의 공개 입구 — 다른 폴더는 여기서만 import 한다. */
export { default as OwnerSignupProvider } from "./components/OwnerSignupProvider";
export { default as StudentSignupProvider } from "./components/StudentSignupProvider";
export { default as TermsSheet } from "./components/TermsSheet";
export { useOwnerSignup } from "./lib/ownerSignupContext";
export { useStudentSignup } from "./lib/studentSignupContext";
export {
  checkBusinessInfo,
  formatBusinessNumber,
  isStoreInfoComplete,
} from "./lib/businessInfo";
export {
  MOCK_VERIFICATION_CODE,
  SCHOOL_EMAIL_DOMAIN,
  VERIFICATION_CODE_TTL_MS,
  formatRemaining,
  isSchoolEmail,
  isStudentInfoComplete,
} from "./lib/studentInfo";
export type {
  BusinessCheck,
  BusinessInfo,
  Certificate,
  OwnerSignupDraft,
  StudentSignupDraft,
} from "./types";

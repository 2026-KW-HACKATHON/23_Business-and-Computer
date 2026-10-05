import { ApiError } from "../../../api/client";
import { saveAccessToken } from "../../auth";
import {
  registerStudent,
  sendStudentEmailCode,
  uploadProfileImage,
  verifyStudentEmailCode,
} from "../api/signupApi";
import type {
  Certificate,
  CertificateStatus,
  EmailCodeSendResult,
  EmailCodeVerifyResult,
  PhotoUploadResult,
  StudentRegisterResult,
  StudentRegistrationRequest,
  StudentSignupDraft,
} from "../types";

/** 광운대 학교 메일만 가입할 수 있다 */
export const SCHOOL_EMAIL_DOMAIN = "@kw.ac.kr";

/** 백엔드가 받는 대학교 이름 (지금은 광운대학교만) */
const UNIVERSITY = "광운대학교";

/** 인증번호 유효 시간 (백엔드와 같은 10분) */
export const VERIFICATION_CODE_TTL_MS = 10 * 60 * 1000;

/** 인증번호를 다시 보낼 수 있을 때까지 (백엔드 기준, 메일 주소가 아니라 사용자마다) */
export const RESEND_COOLDOWN_MS = 60 * 1000;

/** 이만큼 틀리면 백엔드가 맞는 번호도 받지 않아 인증번호를 다시 받아야 한다 */
export const MAX_CODE_ATTEMPTS = 5;

/** 가장 오래된 자격증 취득 연도 (백엔드 @Min) */
const MIN_ACQUIRED_YEAR = 1900;

export function isSchoolEmail(email: string): boolean {
  const value = email.trim().toLowerCase();
  return value.endsWith(SCHOOL_EMAIL_DOMAIN) && value.length > SCHOOL_EMAIL_DOMAIN.length;
}

/** 백엔드와 같은 형식: 숫자 10자리 */
export function isStudentNumber(value: string): boolean {
  return /^\d{10}$/.test(value);
}

/** 1/3 에서 「다음」을 누를 수 있는지 */
export function isStudentInfoComplete(draft: StudentSignupDraft): boolean {
  return (
    draft.name.trim() !== "" &&
    draft.department.trim() !== "" &&
    isStudentNumber(draft.studentNumber) &&
    draft.studentNumber !== draft.takenStudentNumber &&
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

/** 자격증 한 줄: 둘 다 비면 empty, 하나만 있거나 연도가 4자리가 아니면 incomplete, 범위 밖이면 invalidYear */
export function certificateStatus(
  certificate: Certificate,
  thisYear: number = new Date().getFullYear(),
): CertificateStatus {
  const values = [certificate.name, certificate.acquiredYear].map((v) => v.trim());
  if (values.every((v) => v === "")) return "empty";
  if (values.some((v) => v === "") || !/^\d{4}$/.test(certificate.acquiredYear)) return "incomplete";
  const year = Number(certificate.acquiredYear);
  return year >= MIN_ACQUIRED_YEAR && year <= thisYear ? "complete" : "invalidYear";
}

/** 값이 있는데 http(s):// 로 시작하지 않으면 https:// 를 붙인다 (백엔드는 http(s) 주소만 받는다) */
export function normalizePortfolioUrl(input: string): string {
  const value = input.trim();
  if (value === "" || /^https?:\/\//.test(value)) return value;
  return `https://${value}`;
}

/** draft → POST /auth/student 본문. 완전히 빈 자격증 줄은 뺀다 */
export function toStudentRegistration(
  draft: StudentSignupDraft,
  profileImageUrl: string,
): StudentRegistrationRequest {
  return {
    name: draft.name.trim(),
    email: draft.verifiedEmail,
    university: UNIVERSITY,
    studentNumber: draft.studentNumber,
    major: draft.department.trim(),
    portfolioUrl: normalizePortfolioUrl(draft.portfolioUrl),
    introduction: draft.intro.trim(),
    profileImageUrl,
    specialtyIds: draft.specialtyIds,
    certificates: draft.certificates
      .filter((c) => certificateStatus(c) !== "empty")
      .map((c) => ({
        certificateName: c.name.trim(),
        acquiredYear: Number(c.acquiredYear),
      })),
  };
}

/** POST /auth/student-verification/email 결과를 화면이 쓰는 값으로 바꾼다 */
export async function sendVerificationCode(email: string): Promise<EmailCodeSendResult> {
  try {
    await sendStudentEmailCode(email);
    return "sent";
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return "unauthorized";
      if (error.code === "USER_409_REGISTERED") return "alreadyRegistered";
      if (error.code === "USER_409_EMAIL") return "emailTaken";
      if (error.status === 429) return "cooldown";
      if (error.status === 503) return "deliveryFailed";
      // 사전 검사(@kw.ac.kr)를 통과했는데도 400 이면 메일 형식 오류로 본다
      if (error.status === 400) return "invalidEmail";
    }
    return "error";
  }
}

/** POST /auth/student-verification/email/verify 결과를 화면이 쓰는 값으로 바꾼다 */
export async function verifyCode(email: string, code: string): Promise<EmailCodeVerifyResult> {
  try {
    await verifyStudentEmailCode(email, code);
    return "verified";
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return "unauthorized";
      if (error.code === "USER_409_REGISTERED") return "alreadyRegistered";
      // STUDENT_EMAIL_400 (틀림·만료·5번 넘게 틀림) 과 형식 오류
      if (error.status === 400) return "invalid";
    }
    return "error";
  }
}

/** 프로필 사진 업로드. 401 말고는 모두 실패로 본다 (형식·크기는 고를 때 이미 막았다) */
export async function uploadSignupPhoto(file: File): Promise<PhotoUploadResult> {
  try {
    return { status: "uploaded", imageUrl: await uploadProfileImage(file) };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "failed" };
  }
}

/**
 * POST /auth/student 로 가입을 저장한다. 성공하면 화면이 응답을 버려도 토큰이 남도록
 * 여기서 바로 새 access token 을 저장한다.
 */
export async function registerStudentSignup(
  draft: StudentSignupDraft,
  profileImageUrl: string,
): Promise<StudentRegisterResult> {
  try {
    saveAccessToken(await registerStudent(toStudentRegistration(draft, profileImageUrl)));
    return "registered";
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return "unauthorized";
      switch (error.code) {
        case "USER_409_REGISTERED":
          return "alreadyRegistered";
        case "USER_409_EMAIL":
          return "emailTaken";
        case "STUDENT_409_NUMBER":
          return "studentNumberTaken";
        case "STUDENT_EMAIL_403":
          return "reverify";
        case "SPECIALTY_400":
        case "SPECIALTY_400_DUPLICATE":
          return "specialtyInvalid";
      }
    }
    return "error";
  }
}

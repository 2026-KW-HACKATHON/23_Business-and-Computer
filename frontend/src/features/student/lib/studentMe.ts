import { ApiError } from "../../../api/client";
import { normalizePortfolioUrl, uploadSignupPhoto } from "../../signup";
import { fetchStudentMe, updateStudentMe } from "../api/meApi";
import type { StudentMeResponse, StudentMeReview, StudentMeSpecialtyCategory } from "../api/meApi";

/** 내 정보 (GET /students/me) */
export type StudentMe = StudentMeResponse;
export type { StudentMeReview };

export type StudentMeResult =
  | { status: "loaded"; data: StudentMe }
  | { status: "unauthorized" }
  /** 403 — 학생이 아님 */
  | { status: "forbidden" }
  | { status: "error" };

/** 내 정보를 불러온다 */
export async function loadStudentMe(): Promise<StudentMeResult> {
  try {
    return { status: "loaded", data: await fetchStudentMe() };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
    }
    return { status: "error" };
  }
}

/** 입학연도 두 자리 「24」 → 「24학번」. 모양이 다르면 undefined */
export function studentYearText(studentNumber: string | null | undefined): string | undefined {
  const value = studentNumber?.trim() ?? "";
  return /^\d{2}$/.test(value) ? `${value}학번` : undefined;
}

/** 대분류로 묶인 특기의 이름만 차례대로 (겹치면 한 번) */
export function specialtyNamesOf(categories: StudentMeSpecialtyCategory[]): string[] {
  return [...new Set(categories.flatMap((category) => category.specialties.map((s) => s.name)))];
}

/** 내 특기 id (프로필 편집에서 고른 칩) */
export function specialtyIdsOf(me: StudentMe): number[] {
  return me.specialtyCategories.flatMap((category) => category.specialties.map((s) => s.id));
}

/** 포트폴리오 주소를 보여 줄 때는 「https://」를 뺀다 (「behance.net/kwangwoon」) */
export function portfolioLabel(url: string): string {
  return url.replace(/^https:\/\//, "");
}

/** 「열기」 링크 주소. 서버는 http(s) 주소만 받지만 혹시 없으면 https:// 를 붙인다 */
export function portfolioHref(url: string): string {
  return /^https?:\/\//.test(url) ? url : `https://${url}`;
}

/** 후기의 의뢰 자리. 의뢰 제목, 없으면 의뢰의 특기 이름을 잇는다 (「메뉴판·가격표 디자인」) */
export function reviewWorkText(review: StudentMeReview): string {
  return review.jobTitle?.trim() || specialtyNamesOf(review.specialtyCategories).join(" · ");
}

/** 저장할 프로필. PUT /students/me 는 보낸 값으로 모두 바뀌어서 바꾸지 않는 칸도 지금 값을 넣는다 */
export interface StudentMeChanges {
  introduction: string;
  specialtyIds: number[];
  certificates: { certificateName: string; acquiredYear: number }[];
  /** 「https://」 없이 적어도 저장할 때 붙인다 */
  portfolioUrl: string;
  /** 지금 사진 주소. 새 사진을 함께 넘기면 그 사진으로 바뀐다 */
  profileImageUrl: string;
}

/** 지금 내 정보 그대로 (사진만 바꿀 때 등) */
export function studentMeChanges(me: StudentMe): StudentMeChanges {
  return {
    introduction: me.introduction ?? "",
    specialtyIds: specialtyIdsOf(me),
    certificates: me.certificates.map((c) => ({
      certificateName: c.certificateName,
      acquiredYear: c.acquiredYear,
    })),
    portfolioUrl: me.portfolioUrl ?? "",
    profileImageUrl: me.profileImageUrl ?? "",
  };
}

export type StudentMeSaveResult =
  /** 저장한 사진 주소 (새 사진이면 올린 주소) */
  | { status: "saved"; profileImageUrl: string }
  | {
      status:
        | "unauthorized"
        | "forbidden"
        /** 새 사진을 올리지 못함 */
        | "photoFailed"
        /** 400 SPECIALTY_400 · SPECIALTY_400_DUPLICATE — 없거나 겹친 특기 */
        | "invalidSpecialty"
        /** 그 밖의 400 — 적은 내용 확인 */
        | "invalidInput"
        /** 5xx · 네트워크 */
        | "error";
    };

/**
 * 프로필을 저장한다 (PUT /students/me). 새 사진이 있으면 먼저 프로필 사진(PROFILE)으로 올리고
 * 그 주소로 바꾼다. 글자 칸은 앞뒤 빈칸을 빼고, 포트폴리오 주소에는 https:// 를 붙인다.
 */
export async function saveStudentMe(changes: StudentMeChanges, photo?: File): Promise<StudentMeSaveResult> {
  let profileImageUrl = changes.profileImageUrl;
  if (photo) {
    const uploaded = await uploadSignupPhoto(photo);
    if (uploaded.status === "unauthorized") return { status: "unauthorized" };
    if (uploaded.status !== "uploaded") return { status: "photoFailed" };
    profileImageUrl = uploaded.imageUrl;
  }
  try {
    await updateStudentMe({
      profileImageUrl,
      introduction: changes.introduction.trim(),
      specialtyIds: changes.specialtyIds,
      certificates: changes.certificates.map((c) => ({
        certificateName: c.certificateName.trim(),
        acquiredYear: c.acquiredYear,
      })),
      portfolioUrl: normalizePortfolioUrl(changes.portfolioUrl),
    });
    return { status: "saved", profileImageUrl };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
      if (error.code === "SPECIALTY_400" || error.code === "SPECIALTY_400_DUPLICATE") {
        return { status: "invalidSpecialty" };
      }
      if (error.status === 400) return { status: "invalidInput" };
    }
    return { status: "error" };
  }
}

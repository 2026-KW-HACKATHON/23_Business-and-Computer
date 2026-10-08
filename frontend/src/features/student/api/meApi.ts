import { apiData } from "../../../api/client";
import type { SettlementItem } from "./settlementApi";

/** 특기 대분류와 그 아래 특기 (대분류 · 특기 모두 id 순서) */
export interface StudentMeSpecialtyCategory {
  id: number;
  name: string;
  specialties: { id: number; name: string }[];
}

/** 받은 후기 하나 */
export interface StudentMeReview {
  /** 후기를 받은 의뢰 제목 */
  jobTitle?: string | null;
  storeName?: string | null;
  specialtyCategories: StudentMeSpecialtyCategory[];
  rating: number;
  content?: string | null;
  /** 「2026-09-11」 */
  createdAt: string;
}

/** GET /students/me 의 답 (StudentMeResponse). 내 정보 · 프로필 수정 · 프로필 편집이 쓴다 */
export interface StudentMeResponse {
  studentProfileId: number;
  /** 없으면 회색 원 */
  profileImageUrl?: string | null;
  name: string;
  university: string;
  /** 학번 전체가 아니라 입학연도 두 자리 (「24」) */
  studentNumber: string;
  /** 학과 (「경영학부」). 없을 수 있다 */
  major?: string | null;
  introduction?: string | null;
  portfolioUrl?: string | null;
  /** 보낸 제안 수 (취소한 제안 빼고) */
  proposalCount: number;
  completedJobCount: number;
  penaltyCount: number;
  /** 받은 후기 전체의 평균. 후기가 없으면 null */
  averageRating?: number | null;
  /** 내 특기를 대분류로 묶은 것 */
  specialtyCategories: StudentMeSpecialtyCategory[];
  certificates: { certificateName: string; acquiredYear: number }[];
  /** 받은 후기 전체 수 */
  reviewCount: number;
  /** 받은 후기 전부 (최신순) */
  reviews: StudentMeReview[];
  /** 최근 정산 완료 세 개까지 (GET /settlements 와 같은 모양) */
  settlements: SettlementItem[];
}

/**
 * PUT /students/me 본문 (StudentMeUpdateRequest). 보낸 값으로 모두 바뀌므로(없으면 지운다)
 * 바꾸지 않는 칸도 지금 값을 보낸다. 사진 · 포트폴리오 주소는 http(s) 주소나 빈 값, 자격증 연도는 1900 이상
 */
export interface StudentMeUpdateRequest {
  profileImageUrl: string;
  introduction: string;
  specialtyIds: number[];
  certificates: { certificateName: string; acquiredYear: number }[];
  portfolioUrl: string;
}

/** GET /students/me — 로그인한 학생의 프로필과 활동 요약. 학생이 아니면 403 */
export async function fetchStudentMe(): Promise<StudentMeResponse> {
  const data = await apiData<StudentMeResponse | undefined>("/students/me");
  if (!data) throw new Error("Student me response has no data");
  return data;
}

/**
 * PUT /students/me — 프로필 저장. 없거나 겹친 특기는 400 SPECIALTY_400 · SPECIALTY_400_DUPLICATE,
 * 학생이 아니면 403. 답에는 데이터가 없다
 */
export async function updateStudentMe(request: StudentMeUpdateRequest): Promise<void> {
  await apiData<unknown>("/students/me", { method: "PUT", body: JSON.stringify(request) });
}

import type { Role } from "../types/role";

/** 문제 신고를 받는 운영 메일 (피그마 「학생 문제 신고 - 메일 문의 안내」) */
export const SUPPORT_EMAIL = "gkwang0311@gmail.com";

/** 신고하는 쪽마다 고를 문제 종류. 사장님은 학생을, 학생은 사장님을 신고한다 */
export const REPORT_PROBLEM_TYPES: Record<Role, string[]> = {
  owner: ["연락 두절", "작업 불이행", "마감 초과", "빈 결과물", "기타"],
  student: ["연락 두절", "조건과 다른 요구", "확인 지연", "기타"],
};

const REPORT_SUBJECT: Record<Role, string> = {
  owner: "학생 문제 신고",
  student: "사장님 문제 신고",
};

/** 「메일 보내기」: 제목과 적을 칸을 채운 메일 쓰기 주소 */
export function reportMailto(workTitle: string, reporter: Role = "owner"): string {
  const subject = `[${REPORT_SUBJECT[reporter]}] ${workTitle}`;
  const body = [
    `의뢰 이름: ${workTitle}`,
    `문제 종류: (${REPORT_PROBLEM_TYPES[reporter].join(" / ")})`,
    "자세한 설명:",
    "증거 자료: 채팅 화면이나 사진을 붙여 주세요",
  ].join("\n");
  return `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}

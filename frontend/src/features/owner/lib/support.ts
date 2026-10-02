/** 학생 문제 신고를 받는 운영 메일 (피그마 「학생 문제 신고 - 메일 문의 안내」) */
export const SUPPORT_EMAIL = "gkwang0311@gmail.com";

export const REPORT_PROBLEM_TYPES = ["연락 두절", "작업 불이행", "마감 초과", "빈 결과물", "기타"];

/** 「메일 보내기」: 제목과 적을 칸을 채운 메일 쓰기 주소 */
export function reportMailto(workTitle: string): string {
  const subject = `[학생 문제 신고] ${workTitle}`;
  const body = [
    `의뢰 이름: ${workTitle}`,
    `문제 종류: (${REPORT_PROBLEM_TYPES.join(" / ")})`,
    "자세한 설명:",
    "증거 자료: 채팅 화면이나 사진을 붙여 주세요",
  ].join("\n");
  return `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}

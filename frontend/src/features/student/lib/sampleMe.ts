import type { MyProfile } from "../types";

/*
 * 내 프로필 임시 예시 데이터 (피그마 「내 정보 · 설정 (학생)」 · 「프로필 수정(학생)」).
 * 사장님 예시의 김광운 학생 프로필(features/owner/lib/sampleStudents.ts)과 같은 내용이다.
 * 완료 건수 · 평점 · 받은 후기는 작업(sampleWorks)에서 센다.
 */

export const SAMPLE_MY_PROFILE: MyProfile = {
  id: "student-kwangwoon",
  name: "김광운",
  department: "경영학부",
  year: "24학번",
  intro: "메뉴판·로고 디자인을 주로 해요",
  noShowCount: 0,
  badges: ["메뉴판·가격표 디자인", "로고 디자인", "SNS 게시물"],
  certificates: [
    { name: "GTQ 1급", acquiredOn: "2023-08" },
    { name: "ACP (Adobe 인증)", acquiredOn: "2024-02" },
  ],
  portfolioUrl: "behance.net/kwangwoon",
};

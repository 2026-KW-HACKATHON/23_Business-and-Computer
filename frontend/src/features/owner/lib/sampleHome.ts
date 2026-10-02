import type { OwnerHome } from "../types";

/**
 * 임시 예시 데이터 (피그마 「사장님 홈」 내용 그대로).
 * 백엔드 연동 전까지 useOwnerHome 이 돌려준다.
 */
export const SAMPLE_OWNER_HOME: OwnerHome = {
  hasUnreadNotifications: true,
  todos: [
    {
      type: "draftArrived",
      id: "req-101",
      kind: "request",
      title: "인스타 게시물 5개 제작",
      field: "홍보",
      student: { name: "박지은", department: "시각디자인학과" },
      autoCompleteOn: "2026-09-29",
    },
    {
      type: "proposalArrived",
      id: "prop-201",
      kind: "proposal",
      title: "카카오 맵 수정",
      field: "홍보",
      student: { name: "박누리", department: "미디어커뮤니케이션학부" },
      empathyCount: 27,
    },
    {
      type: "applicants",
      id: "req-102",
      kind: "request",
      title: "영어·중국어 메뉴판 번역",
      field: "글쓰기·번역",
      budget: 50000,
      applicantCount: 5,
      draftDue: "2026-09-27",
    },
  ],
  working: [
    {
      id: "req-103",
      kind: "request",
      title: "메뉴판 디자인 변경",
      student: { name: "김광운" },
      stage: "draft",
      due: "2026-09-29",
      chatId: "chat-301",
    },
  ],
  waiting: [
    {
      id: "req-104",
      kind: "request",
      title: "봄 신메뉴 전단지",
      stage: "draft",
      due: "2026-10-03",
      status: "recruiting",
    },
  ],
  examples: [
    { id: "example-review", field: "분석", title: "손님 리뷰에서\n아쉬운 점을 찾아 드려요" },
    { id: "example-booking", field: "개발·IT", title: "전화 대신 받는\n예약서를 만들어 드려요" },
    { id: "example-coupon", field: "디자인", title: "단골 쿠폰·도장카드를\n만들어 드려요" },
  ],
  done: [
    {
      id: "req-090",
      kind: "request",
      title: "메뉴판 제작",
      student: { name: "김광운" },
      completedOn: "2026-09-12",
    },
    {
      id: "req-085",
      kind: "request",
      title: "인스타 계정 만들기",
      student: { name: "정하은" },
      completedOn: "2026-09-03",
    },
    {
      id: "prop-150",
      kind: "proposal",
      title: "네이버 지도 사진 정리",
      student: { name: "박누리" },
      completedOn: "2026-08-26",
    },
    {
      id: "req-070",
      kind: "request",
      title: "가게 로고 만들기",
      student: { name: "이은서" },
      completedOn: "2026-08-14",
    },
  ],
};

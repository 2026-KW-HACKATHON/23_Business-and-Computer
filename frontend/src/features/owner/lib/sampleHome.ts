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
      id: "work-101",
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
      id: "work-103",
      kind: "request",
      title: "메뉴판 디자인 변경",
      student: { name: "김광운" },
      stage: "draft",
      due: "2026-09-29",
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
    {
      id: "example-review",
      field: "분석",
      task: "리뷰 분석",
      title: "손님 리뷰에서\n아쉬운 점을 찾아 드려요",
      content: {
        title: "손님 리뷰 분석",
        description:
          "네이버·카카오 리뷰에서 손님들이 아쉬워하는 점을 정리해 주세요. 무엇부터 고치면 좋을지도 알려 주면 좋겠어요.",
        budget: 40_000,
        draftDue: "2026-09-30",
        finalDue: "2026-10-06",
        revisions: 1,
      },
    },
    {
      id: "example-booking",
      field: "개발·IT",
      task: "온라인 예약·주문서",
      title: "전화 대신 받는\n예약서를 만들어 드려요",
      content: {
        title: "온라인 예약서 만들기",
        description:
          "점심·단체 예약을 전화로만 받아서 바쁠 때 놓쳐요. 손님이 휴대폰으로 날짜와 인원을 적는 예약서를 만들어 주세요.",
        budget: 50_000,
        draftDue: "2026-10-02",
        finalDue: "2026-10-08",
        revisions: 1,
      },
    },
    {
      id: "example-coupon",
      field: "디자인",
      task: "쿠폰·스티커·명함 디자인",
      title: "단골 쿠폰·도장카드를\n만들어 드려요",
      content: {
        title: "단골 쿠폰·도장카드 디자인",
        description:
          "10번 오면 음료 한 잔을 주는 도장카드를 만들고 싶어요. 가게 분위기에 맞게 디자인하고 인쇄용 파일로 주세요.",
        budget: 30_000,
        draftDue: "2026-09-29",
        finalDue: "2026-10-02",
        revisions: 1,
      },
    },
  ],
  done: [
    {
      id: "work-090",
      kind: "request",
      title: "메뉴판 제작",
      student: { name: "김광운" },
      completedOn: "2026-09-12",
    },
    {
      id: "work-085",
      kind: "request",
      title: "인스타 계정 만들기",
      student: { name: "정하은" },
      completedOn: "2026-09-03",
    },
    {
      id: "work-080",
      kind: "proposal",
      title: "네이버 지도 사진 정리",
      student: { name: "박누리" },
      completedOn: "2026-08-26",
    },
    {
      id: "work-070",
      kind: "request",
      title: "가게 로고 만들기",
      student: { name: "이은서" },
      completedOn: "2026-08-14",
    },
  ],
};

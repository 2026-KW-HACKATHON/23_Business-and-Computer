import type { ExploreDetail } from "../types";

/*
 * 탐색 상세 임시 예시 데이터 (피그마 「제안서 보기 · 의뢰서 보기 (다른 가게 · 읽기 전용)」).
 * 탐색 목록(sampleTabs.ts)과 같은 id 를 쓴다.
 */

export const SAMPLE_EXPLORE_DETAILS: ExploreDetail[] = [
  {
    id: "prop-501",
    kind: "proposal",
    title: "인기 메뉴를 강조한 메뉴판 시안",
    field: "디자인",
    storeName: "월계분식",
    receivedOn: "2026-09-18",
    statusLabel: "수락됨",
    empathyCount: 34,
    student: {
      id: "student-eunseo",
      name: "이은서",
      department: "시각디자인학과",
      year: "23학번",
      rating: 4.9,
      completedCount: 6,
    },
    problem:
      "메뉴가 40개 넘게 빽빽해서 처음 온 손님은 뭘 시킬지 한참 고민해요. 인기 메뉴가 어디 있는지도 안 보여요.",
    solution:
      "인기 메뉴 5개를 사진과 함께 맨 위에 크게 넣고, 나머지는 종류별로 묶어 한눈에 보이게 정리해 드릴게요.",
    attachments: ["IMG_2031.jpg", "메뉴판_시안.png"],
  },
  {
    id: "req-502",
    kind: "request",
    title: "영어·중국어 메뉴판 번역",
    field: "글쓰기·번역",
    storeName: "광운카페",
    tasks: ["영어 번역", "중국어 번역"],
    description:
      "외국인 손님이 늘어서 메뉴판을 영어랑 중국어로 바꾸고 싶어요. 메뉴는 음료 20개, 디저트 12개로 모두 32개예요. 견과류처럼 알레르기가 있을 수 있는 재료도 함께 표시해 주면 좋겠어요.",
    attachments: ["메뉴판_음료.jpg", "메뉴판_디저트.jpg"],
  },
  {
    id: "prop-503",
    kind: "proposal",
    title: "시험 기간 광운대생 이벤트 기획",
    field: "홍보",
    storeName: "월계반점",
    receivedOn: "2026-09-28",
    statusLabel: "수락 대기",
    empathyCount: 21,
    student: {
      id: "student-nuri",
      name: "박누리",
      department: "미디어커뮤니케이션학부",
      year: "21학번",
      rating: 4.8,
      completedCount: 3,
    },
    problem:
      "시험 기간에는 학생 손님이 확 줄어요. 근처 카페에서 공부하다가 저녁도 거기서 간단히 해결하는 것 같아요.",
    solution:
      "학생증을 보여 주면 음료를 주는 시험 기간 이벤트를 기획하고, 인스타 공지와 매장 포스터까지 만들어 드릴게요.",
    attachments: ["시험기간_포스터_예시.png"],
  },
  {
    id: "req-504",
    kind: "request",
    title: "네이버 플레이스 매장 사진 촬영",
    field: "홍보",
    storeName: "골목카페",
    tasks: ["음식·매장 사진"],
    description:
      "네이버 플레이스에 올릴 매장과 메뉴 사진을 새로 찍어 주세요. 낮과 저녁 분위기가 모두 보이면 좋겠어요.",
    attachments: ["지금_대표사진.jpg"],
  },
  {
    id: "prop-505",
    kind: "proposal",
    title: "손님 리뷰로 본 아쉬운 점 정리",
    field: "분석",
    storeName: "동네빵집",
    receivedOn: "2026-09-24",
    statusLabel: "수락 대기",
    empathyCount: 12,
    student: {
      id: "student-haeun",
      name: "정하은",
      department: "영어영문학과",
      year: "22학번",
      rating: 4.9,
      completedCount: 4,
    },
    problem: "리뷰에 「빵이 금방 떨어져요」라는 말이 자주 보여요. 오후에 오면 살 게 없다는 손님이 많아요.",
    solution:
      "최근 리뷰 200개를 읽고 아쉬운 점을 종류별로 묶어, 무엇부터 고치면 좋을지 한 장으로 정리해 드릴게요.",
    attachments: ["리뷰_캡처.png"],
  },
  {
    id: "req-506",
    kind: "request",
    title: "전화 대신 받는 온라인 예약서",
    field: "개발·IT",
    storeName: "광운헤어",
    tasks: ["온라인 예약·주문서"],
    description:
      "주말에 예약 전화가 몰려서 놓치는 손님이 많아요. 손님이 휴대폰으로 날짜·시간·시술을 고르는 예약서를 만들어 주세요.",
    attachments: ["예약_장부.jpg"],
  },
];

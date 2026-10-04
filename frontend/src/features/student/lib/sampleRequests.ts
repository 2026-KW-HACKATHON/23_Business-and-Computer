import { day, dayAt } from "../../../lib/sampleTime";
import type { StudentApplication, StudentRequest } from "../types";

/*
 * 가게 의뢰 임시 예시 데이터 (피그마 「학생 탐색 · 의뢰 상세 · 의뢰서 전체 보기 · 지원하기」).
 * 치킨플러스 의뢰(req-102 · 104 · 105)는 사장님 예시의 보낸 의뢰, 다른 가게 의뢰(req-502 · 504 ·
 * 506 · 507)는 사장님 탐색과 같은 의뢰라 제목 · 마감 · 상태가 같다.
 */

export const SAMPLE_REQUESTS: StudentRequest[] = [
  {
    id: "req-104",
    title: "봄 신메뉴 전단지",
    field: "디자인",
    store: { id: "store-chicken", name: "치킨플러스" },
    budget: 40000,
    draftDue: day(11),
    finalDue: day(15),
    revisionLimit: 1,
    tasks: ["전단지·포스터 디자인"],
    description:
      "봄 신메뉴 3가지(허니갈릭, 치즈볼, 양념 반반)를 알리는 A4 전단지를 만들고 싶어요. 광운대 앞에서 나눠 줄 거라 학생들이 좋아할 느낌이면 좋겠어요.",
    attachments: ["신메뉴_사진.jpg", "지난_전단지.jpg"],
    progress: "recruiting",
    createdAt: dayAt(-1, 18, 0),
  },
  {
    id: "req-502",
    title: "영어·중국어 메뉴판 번역",
    field: "글쓰기·번역",
    store: { id: "store-kwcafe", name: "광운카페" },
    budget: 50000,
    draftDue: day(6),
    finalDue: day(9),
    revisionLimit: 1,
    tasks: ["영어 번역", "중국어 번역"],
    description:
      "외국인 손님이 늘어서 메뉴판을 영어랑 중국어로 바꾸고 싶어요. 메뉴는 음료 20개, 디저트 12개로 모두 32개예요. 견과류처럼 알레르기가 있을 수 있는 재료도 함께 표시해 주면 좋겠어요.",
    attachments: ["메뉴판_음료.jpg", "메뉴판_디저트.jpg"],
    progress: "recruiting",
    createdAt: dayAt(-2, 15, 0),
  },
  {
    id: "req-105",
    title: "가게 소개 홈페이지",
    field: "개발·IT",
    store: { id: "store-chicken", name: "치킨플러스" },
    budget: 100000,
    draftDue: day(8),
    finalDue: day(15),
    revisionLimit: 2,
    tasks: ["가게 홈페이지"],
    description:
      "메뉴와 영업시간, 오시는 길을 한 화면에서 볼 수 있는 간단한 가게 소개 홈페이지를 만들고 싶어요.",
    attachments: ["가게_외관.jpg"],
    progress: "recruiting",
    createdAt: dayAt(-3, 9, 30),
  },
  {
    id: "req-507",
    title: "빵 가격표 새로 만들기",
    field: "디자인",
    store: { id: "store-bakery", name: "동네빵집" },
    budget: 40000,
    draftDue: day(5),
    finalDue: day(8),
    revisionLimit: 1,
    tasks: ["메뉴판·가격표 디자인"],
    description:
      "진열대 빵마다 붙일 작은 가격표를 새로 만들고 싶어요. 빵 이름과 가격이 멀리서도 잘 보이면 좋겠어요.",
    attachments: ["지금_가격표.jpg"],
    progress: "recruiting",
    createdAt: dayAt(-4, 10, 0),
  },
  {
    id: "req-102",
    title: "영어·중국어 메뉴판 번역",
    field: "글쓰기·번역",
    store: { id: "store-chicken", name: "치킨플러스" },
    budget: 50000,
    draftDue: day(5),
    finalDue: day(8),
    revisionLimit: 1,
    tasks: ["영어 번역", "중국어 번역"],
    description:
      "메뉴 32개를 영어와 중국어로 번역해 주세요. 외국인 손님이 늘어서 메뉴판만 보고 바로 고를 수 있으면 좋겠어요.",
    attachments: ["지금_메뉴판.jpg"],
    progress: "recruiting",
    createdAt: dayAt(-5, 9, 0),
  },
  {
    id: "req-504",
    title: "네이버 플레이스 매장 사진 촬영",
    field: "홍보",
    store: { id: "store-alley", name: "골목카페" },
    budget: 50000,
    draftDue: day(-12),
    finalDue: day(-8),
    revisionLimit: 1,
    tasks: ["음식·매장 사진"],
    description:
      "네이버 플레이스에 올릴 매장과 메뉴 사진을 새로 찍어 주세요. 낮과 저녁 분위기가 모두 보이면 좋겠어요.",
    attachments: ["지금_대표사진.jpg"],
    progress: "completed",
    createdAt: dayAt(-6, 9, 0),
  },
  {
    id: "req-506",
    title: "전화 대신 받는 온라인 예약서",
    field: "개발·IT",
    store: { id: "store-kwhair", name: "광운헤어" },
    budget: 80000,
    draftDue: day(4),
    finalDue: day(9),
    revisionLimit: 1,
    tasks: ["온라인 예약·주문서"],
    description:
      "주말에 예약 전화가 몰려서 놓치는 손님이 많아요. 손님이 휴대폰으로 날짜·시간·시술을 고르는 예약서를 만들어 주세요.",
    attachments: ["예약_장부.jpg"],
    progress: "recruiting",
    createdAt: dayAt(-9, 11, 0),
  },
  {
    id: "req-510",
    title: "쿠폰·스티커 디자인",
    field: "디자인",
    store: { id: "store-dino", name: "공룡카페" },
    budget: 40000,
    draftDue: day(3),
    finalDue: day(6),
    revisionLimit: 1,
    tasks: ["쿠폰·스티커·명함 디자인"],
    description: "공룡 캐릭터를 넣은 할인 쿠폰과 포장용 스티커를 만들어 주세요.",
    attachments: ["로고.png"],
    progress: "closed",
    createdAt: dayAt(-12, 14, 0),
  },
];

export const SAMPLE_APPLICATIONS: StudentApplication[] = [
  {
    requestId: "req-502",
    appliedOn: day(-1),
    status: "reviewing",
    plan: {
      summary: "메뉴 32개를 영어·중국어로 번역하고, 유학생 친구에게 자연스러운지 검수까지 받을게요.",
      method:
        "메뉴 이름은 소리 나는 대로 적고(예: Tteokbokki), 아래에 재료와 맛을 한 줄로 설명해요. 알레르기 재료는 아이콘으로 표시할게요.",
      deliverable: "인쇄용 PDF와 바로 고칠 수 있는 한글(HWP) 파일로 드려요.",
    },
  },
  {
    requestId: "req-507",
    appliedOn: day(-3),
    status: "reviewing",
    plan: {
      summary: "빵 이름과 가격이 멀리서도 보이는 가격표 30장을 만들어 드릴게요.",
      method: "진열대 높이에 맞춰 글자 크기를 정하고, 인기 빵에는 「추천」 표시를 넣어요.",
      deliverable: "바로 인쇄할 수 있는 PDF와 고칠 수 있는 원본 파일로 드려요.",
    },
  },
  {
    requestId: "req-510",
    appliedOn: day(-11),
    status: "notSelected",
    plan: {
      summary: "공룡 캐릭터를 살린 쿠폰과 스티커를 한 벌로 맞춰 드릴게요.",
      method: "로고의 색을 그대로 쓰고, 스티커는 원형 · 사각 두 가지로 만들어요.",
      deliverable: "인쇄용 PDF와 원본 파일",
    },
  },
];

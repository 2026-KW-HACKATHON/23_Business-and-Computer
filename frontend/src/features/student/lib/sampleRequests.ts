import { day } from "../../../lib/sampleTime";
import type { StudentApplication, StudentRequest } from "../types";

/*
 * 지원한 의뢰 임시 예시 데이터 (내 활동 「지원한 의뢰」 · 홈 「기다리는 중」).
 * req-502 · 507 은 사장님 탐색과 같은 의뢰라 제목 · 마감이 같다.
 */

export const SAMPLE_REQUESTS: StudentRequest[] = [
  {
    id: "req-502",
    title: "영어·중국어 메뉴판 번역",
    field: "글쓰기·번역",
    store: { id: "store-kwcafe", name: "광운카페" },
    budget: 50000,
    draftDue: day(6),
    finalDue: day(9),
    revisionLimit: 1,
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

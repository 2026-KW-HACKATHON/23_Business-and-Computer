import type { OwnerPayment, OwnerStore, PaymentSummary } from "../types";

/*
 * 임시 예시 데이터 (피그마 「가게 정보 수정 · 결제 내역」 내용).
 * 결제 내역은 작업 예시(sampleDetails)와 같은 id 를 쓴다.
 */

export const SAMPLE_STORE: OwnerStore = {
  storeName: "치킨플러스",
  category: "음식점",
  address: "서울 노원구 석계로 13길 35",
  addressDetail: "세영청마루아파트 상가동 1층 101호",
  phone: "02-949-1234",
  intro: "광운대 앞 20년 된 치킨집이에요. 학생 손님이 많아요.",
  representative: "이새빛",
  businessNumber: "123-45-67890",
};

export const SAMPLE_PAYMENTS: OwnerPayment[] = [
  {
    id: "pay-101",
    workId: "work-101",
    title: "인스타 게시물 5개 제작",
    studentName: "박지은",
    amount: 50000,
    paidOn: "2026-09-20",
    status: "escrowed",
  },
  {
    id: "pay-103",
    workId: "work-103",
    title: "메뉴판 디자인 변경",
    studentName: "김광운",
    amount: 60000,
    paidOn: "2026-09-20",
    status: "escrowed",
  },
  {
    id: "pay-090",
    workId: "work-090",
    title: "메뉴판 제작",
    studentName: "김광운",
    amount: 50000,
    paidOn: "2026-09-05",
    status: "settled",
    settledOn: "2026-09-12",
  },
  {
    id: "pay-085",
    workId: "work-085",
    title: "인스타 계정 만들기",
    studentName: "정하은",
    amount: 30000,
    paidOn: "2026-08-27",
    status: "settled",
    settledOn: "2026-09-03",
    autoCompleted: true,
  },
  {
    id: "pay-080",
    workId: "work-080",
    title: "네이버 지도 사진 정리",
    studentName: "박누리",
    amount: 25000,
    paidOn: "2026-08-20",
    status: "settled",
    settledOn: "2026-08-26",
  },
  {
    id: "pay-070",
    workId: "work-070",
    title: "가게 로고 만들기",
    studentName: "이은서",
    amount: 70000,
    paidOn: "2026-08-05",
    status: "settled",
    settledOn: "2026-08-14",
  },
  {
    id: "pay-060",
    workId: "work-060",
    title: "간판 시안",
    studentName: "이은서",
    amount: 80000,
    paidOn: "2026-07-28",
    status: "partialRefund",
    refund: { on: "2026-08-10", amount: 64000 },
  },
];

export const SAMPLE_PAYMENT_SUMMARY: PaymentSummary = {
  thisMonth: 110000,
  escrowed: 110000,
  settled: 175000,
};

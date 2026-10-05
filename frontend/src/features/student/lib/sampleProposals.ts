import { day } from "../../../lib/sampleTime";
import type { MyProposal, ProposalExample } from "../types";

/*
 * 제안 임시 예시 데이터. 내가 보낸 제안 (피그마 「내 활동 - 보낸 제안 · 보낸 제안서 상세 보기」),
 * 홈 예시.
 */

export const SAMPLE_MY_PROPOSALS: MyProposal[] = [
  {
    id: "prop-301",
    title: "리뷰 안내문",
    field: "홍보",
    tasks: ["홍보·이벤트 기획"],
    store: { id: "store-dino", name: "공룡카페" },
    sentOn: day(-3),
    empathyCount: 21,
    status: "waiting",
    seenByOwner: true,
    problem: "리뷰를 쓰면 음료를 주는데 안내가 없어서 아무도 몰라요.",
    solution: "테이블마다 놓을 리뷰 안내문을 만들고, 계산대 옆 작은 포스터도 함께 만들어 드릴게요.",
    plan: "· 방법: 손님 눈에 잘 띄는 크기로 안내문 2가지를 만들어요.\n· 일정: 수락되면 이틀 안에 초안, 수정 요청이 오면 나흘 안에 최종본을 드려요.",
    wishBudget: 20000,
    draftDays: 2,
    finalDays: 4,
    attachments: ["테이블_사진.jpg"],
  },
  {
    id: "prop-304",
    title: "시험 기간 학생 할인 이벤트",
    field: "홍보",
    tasks: ["홍보·이벤트 기획", "SNS 게시물"],
    store: { id: "store-kwcafe", name: "광운카페" },
    sentOn: day(-4),
    empathyCount: 12,
    status: "waiting",
    seenByOwner: false,
    problem:
      "시험 기간에 밤늦게까지 공부할 카페를 찾는데, 학생 할인이 없어서 다른 카페로 가게 돼요.",
    solution:
      "시험 기간 2주 동안 학생증을 보여 주면 음료를 1,000원 할인하는 이벤트를 기획하고, 포스터와 인스타 공지를 만들어 드릴게요.",
    plan: "· 방법: 학생 손님 10명에게 원하는 할인을 먼저 물어보고 이벤트 내용을 정해요.\n· 일정: 이틀 안에 기획안과 포스터 초안을 보내 드리고, 수정 요청이 오면 나흘 안에 최종본을 드려요.",
    wishBudget: 30000,
    draftDays: 2,
    finalDays: 4,
    attachments: ["IMG_4821.jpg", "시험기간_포스터_예시.png"],
  },
  {
    id: "prop-303",
    title: "외국어 메뉴판 만들기",
    field: "글쓰기·번역",
    tasks: ["메뉴판·가격표 디자인", "영어 번역"],
    store: { id: "store-bunsik", name: "월계분식" },
    sentOn: day(-5),
    empathyCount: 12,
    status: "accepted",
    seenByOwner: true,
    problem: "유학생 친구랑 오면 메뉴 설명이 어려워요. 사진 없이 한글로만 적혀 있어요.",
    solution: "메뉴 32개를 영어·중국어로 옮기고, 사진을 넣은 메뉴판으로 다시 만들어 드릴게요.",
    plan: "· 방법: 메뉴 이름은 소리 나는 대로 적고, 재료와 맛을 한 줄로 설명해요.\n· 일정: 초안 2일, 수정 뒤 최종본까지 4일 걸려요.\n· 결과물: 사진을 넣은 인쇄용 메뉴판 PDF",
    wishBudget: 50000,
    draftDays: 2,
    finalDays: 4,
    attachments: ["IMG_4821.jpg", "영문메뉴_시안.png"],
    workId: "work-213",
  },
  {
    id: "prop-306",
    title: "배달앱 대표 사진 바꾸기",
    field: "홍보",
    tasks: ["음식·매장 사진"],
    store: { id: "store-banjeom", name: "월계반점" },
    sentOn: day(-8),
    empathyCount: 8,
    status: "waiting",
    seenByOwner: false,
    problem: "배달앱 사진이 어두워서 맛이 잘 안 보여요.",
    solution: "인기 메뉴 5개를 밝은 곳에서 다시 찍어 배달앱 대표 사진으로 바꿔 드릴게요.",
    plan: "· 방법: 점심 장사 전에 자연광으로 찍고, 색을 맛있어 보이게 보정해요.\n· 일정: 수락되면 이틀 안에 사진 초안을 드려요.",
    wishBudget: 30000,
    draftDays: 2,
    finalDays: 3,
    attachments: ["지금_배달앱_캡처.png"],
  },
];

export const SAMPLE_PROPOSAL_EXAMPLES: ProposalExample[] = [
  {
    id: "ex-review",
    field: "분석",
    task: "리뷰 분석",
    title: "리뷰를 모아 손님 반응을\n정리해 드려요",
    proposalTitle: "리뷰로 본 손님 반응 정리",
  },
  {
    id: "ex-event",
    field: "홍보",
    task: "홍보·이벤트 기획",
    title: "시험 기간 학생 이벤트를\n기획해 드려요",
    proposalTitle: "시험 기간 학생 이벤트 기획",
  },
  {
    id: "ex-price",
    field: "디자인",
    task: "메뉴판·가격표 디자인",
    title: "가격표를 한눈에 보이게\n다시 만들어 드려요",
    proposalTitle: "한눈에 보이는 가격표",
  },
];

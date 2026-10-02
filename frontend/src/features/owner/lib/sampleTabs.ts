import type { ExploreItem, OwnerChatRoom, OwnerNotification, OwnerProfile } from "../types";
import { minutesAgo, yesterdayAt } from "./sampleTime";

/*
 * 임시 예시 데이터 (피그마 「사장님 탐색 · 채팅 목록 · 알림 · 내 정보」 내용).
 * 백엔드 연동 전까지 useOwnerData 의 훅들이 돌려준다.
 */

export const SAMPLE_EXPLORE: ExploreItem[] = [
  {
    id: "prop-501",
    kind: "proposal",
    title: "인기 메뉴를 강조한 메뉴판 시안",
    field: "디자인",
    storeName: "월계분식",
    street: "석계로",
    empathyCount: 34,
    createdAt: "2026-09-30T10:00:00+09:00",
  },
  {
    id: "req-502",
    kind: "request",
    title: "영어·중국어 메뉴판 번역",
    field: "글쓰기·번역",
    storeName: "광운카페",
    street: "광운로",
    deadline: { stage: "draft", due: "2026-09-27" },
    createdAt: "2026-09-29T15:00:00+09:00",
  },
  {
    id: "prop-503",
    kind: "proposal",
    title: "시험 기간 광운대생 이벤트 기획",
    field: "홍보",
    storeName: "월계반점",
    street: "광운로",
    empathyCount: 21,
    progress: "waitingAcceptance",
    createdAt: "2026-09-28T12:00:00+09:00",
  },
  {
    id: "req-504",
    kind: "request",
    title: "네이버 플레이스 매장 사진 촬영",
    field: "홍보",
    storeName: "골목카페",
    street: "월계로",
    progress: "completed",
    createdAt: "2026-09-25T09:00:00+09:00",
  },
  {
    id: "prop-505",
    kind: "proposal",
    title: "손님 리뷰로 본 아쉬운 점 정리",
    field: "분석",
    storeName: "동네빵집",
    street: "석계로",
    empathyCount: 12,
    createdAt: "2026-09-24T18:00:00+09:00",
  },
  {
    id: "req-506",
    kind: "request",
    title: "전화 대신 받는 온라인 예약서",
    field: "개발·IT",
    storeName: "광운헤어",
    street: "광운로",
    deadline: { stage: "final", due: "2026-10-10" },
    createdAt: "2026-09-22T11:00:00+09:00",
  },
];

export const SAMPLE_CHATS: OwnerChatRoom[] = [
  {
    workId: "work-103",
    student: { name: "김광운" },
    workTitle: "메뉴판 디자인 변경",
    progress: { type: "drafting", due: "2026-09-29" },
    lastMessage: "감사합니다! 초록 계열로 시안 2개 만들어서 금요일 오전까지 보내드릴게요.",
    lastMessageAt: yesterdayAt(14, 22),
    unreadCount: 1,
  },
  {
    workId: "work-101",
    student: { name: "박지은" },
    workTitle: "인스타 게시물 5개 제작",
    progress: { type: "draftSubmitted" },
    lastMessage: "초안 5장 올렸어요. 확인 부탁드려요!",
    lastMessageAt: yesterdayAt(10, 5),
    unreadCount: 2,
  },
  {
    workId: "work-070",
    student: { name: "이은서" },
    workTitle: "가게 로고 만들기",
    progress: { type: "completed" },
    lastMessage: "후기 남겨 주셔서 감사합니다 :)",
    lastMessageAt: "2026-08-14T17:30:00+09:00",
    unreadCount: 0,
  },
];

export const SAMPLE_NOTIFICATIONS: OwnerNotification[] = [
  {
    id: "noti-1",
    type: "DRAFT_SUBMITTED",
    title: "초안이 도착했어요",
    body: "인스타 게시물 5개 제작, 박지은 학생이 초안을 보냈어요. 확인해 주세요.",
    createdAt: minutesAgo(10),
    read: false,
    targetId: "work-101",
  },
  {
    id: "noti-2",
    type: "PROPOSAL_RECEIVED",
    title: "새 제안이 왔어요",
    body: "카카오 맵 수정, 박누리 학생, 광운대생 손님 27명이 공감했어요",
    createdAt: minutesAgo(120),
    read: false,
    targetId: "prop-201",
  },
  {
    id: "noti-3",
    type: "APPLICATION_RECEIVED",
    title: "지원자가 생겼어요",
    body: "영어·중국어 메뉴판 번역, 지원자가 5명이 됐어요. 작업계획서를 살펴보세요.",
    createdAt: minutesAgo(180),
    read: false,
    targetId: "req-102",
  },
  {
    id: "noti-4",
    type: "CHAT_MESSAGE",
    title: "김광운 학생의 새 메시지",
    body: "감사합니다! 초록 계열로 시안 2개 만들어서 금요일 오전까지 보내드릴게요.",
    createdAt: yesterdayAt(14, 22),
    read: true,
    targetId: "work-103",
  },
  {
    id: "noti-5",
    type: "PAYMENT_ESCROWED",
    title: "안전결제가 완료됐어요",
    body: "메뉴판 디자인 변경, 김광운 학생, 60,000원을 가꿈이 보관해요",
    createdAt: "2026-09-20T13:00:00+09:00",
    read: true,
    targetId: "work-103",
  },
  {
    id: "noti-6",
    type: "WORK_COMPLETED",
    title: "작업이 완료됐어요",
    body: "가게 로고 만들기, 이은서 학생에게 작업비 70,000원을 보냈어요.",
    createdAt: "2026-08-14T17:00:00+09:00",
    read: true,
    targetId: "work-070",
  },
];

export const SAMPLE_PROFILE: OwnerProfile = {
  storeName: "치킨플러스",
  ownerName: "이새빛",
  address: "서울 노원구 석계로 13길 35\n세영청마루아파트 상가동 1층 101호",
  businessVerified: true,
  counts: { sent: 3, proposals: 1, inProgress: 2, done: 4 },
};

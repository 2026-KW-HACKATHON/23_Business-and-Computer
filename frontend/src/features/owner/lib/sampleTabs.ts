import type { OwnerNotification } from "../types";
import { dayAt, minutesAgo, yesterdayAt } from "./sampleTime";

/*
 * 알림 임시 예시 데이터 (피그마 「알림 (사장님)」 내용). 시각은 지금 기준이고,
 * 가리키는 작업 · 제안 · 의뢰와 날짜가 맞는다 (예: 결제 완료 = 그 작업을 결제한 날).
 */

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
    body: "카카오 맵 수정, 박누리 학생, 학생 손님 27명이 공감했어요",
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
    createdAt: dayAt(-2, 13, 50),
    read: true,
    targetId: "work-103",
  },
  {
    id: "noti-6",
    type: "WORK_COMPLETED",
    title: "작업이 완료됐어요",
    body: "가게 로고 만들기, 이은서 학생에게 작업비 70,000원을 보냈어요.",
    createdAt: dayAt(-39, 17, 0),
    read: true,
    targetId: "work-070",
  },
];

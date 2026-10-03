import { formatMonthDay } from "../../../lib/date";
import { day, dayAt, minutesAgo } from "../../../lib/sampleTime";
import type { StudentNotification } from "../types";
import { ACCEPTED_AT, LAST_REVISION_AT } from "./sampleWorks";

/*
 * 알림 임시 예시 데이터 (피그마 「알림 (학생)」 내용). 시각은 지금 기준이고,
 * 가리키는 작업 · 제안 · 의뢰와 날짜가 맞는다.
 */

export const SAMPLE_NOTIFICATIONS: StudentNotification[] = [
  {
    id: "noti-1",
    type: "PROPOSAL_ACCEPTED",
    title: "제안이 수락됐어요",
    body: "외국어 메뉴판 만들기, 월계분식 사장님이 의뢰서를 보냈어요. 확인해 주세요.",
    createdAt: ACCEPTED_AT,
    read: false,
    targetId: "work-213",
  },
  {
    id: "noti-2",
    type: "EMPATHY_GROWN",
    title: "내 제안에 공감이 늘었어요",
    body: "시험 기간 학생 할인 이벤트, 학생 손님 12명이 공감했어요",
    createdAt: minutesAgo(180),
    read: false,
    targetId: "prop-304",
  },
  {
    id: "noti-3",
    type: "REVISION_REQUESTED",
    title: "수정 요청이 왔어요",
    body: "인스타 게시물 5개 제작, 광운카페 사장님이 고칠 곳을 보냈어요.",
    createdAt: LAST_REVISION_AT,
    read: false,
    targetId: "work-211",
  },
  {
    id: "noti-4",
    type: "CHAT_MESSAGE",
    title: "광운카페 사장님의 새 메시지",
    body: "4번에는 시험 기간 이벤트 문구도 넣어 주세요.",
    createdAt: dayAt(-1, 18, 42),
    read: false,
    targetId: "work-211",
  },
  {
    id: "noti-5",
    type: "DUE_SOON",
    title: "수정안 마감을 알려 드려요",
    body: `인스타 게시물 5개 제작, ${formatMonthDay(day(2))}까지 수정안을 제출해 주세요.`,
    createdAt: dayAt(-1, 9, 0),
    read: true,
    targetId: "work-211",
  },
  {
    id: "noti-6",
    type: "SELECTED",
    title: "선정됐어요",
    body: "메뉴판 디자인 변경, 치킨플러스 사장님이 나를 골랐어요. 작업을 시작해 주세요.",
    createdAt: dayAt(-2, 13, 50),
    read: true,
    targetId: "work-103",
  },
  {
    id: "noti-7",
    type: "NOT_SELECTED",
    title: "지원 결과가 나왔어요",
    body: "쿠폰·스티커 디자인, 공룡카페 사장님이 이번에는 다른 학생을 골랐어요.",
    createdAt: dayAt(-6, 17, 30),
    read: true,
    targetId: "req-510",
  },
  {
    id: "noti-8",
    type: "SETTLED",
    title: "작업비가 정산됐어요",
    body: "메뉴판 제작, 치킨플러스, 50,000원이 정산됐어요",
    createdAt: dayAt(-10, 15, 10),
    read: true,
    targetId: "work-090",
  },
  {
    id: "noti-10",
    type: "WORK_CANCELED",
    title: "사장님이 작업을 취소했어요",
    body: "현수막 시안, 해피헤어 사장님 사정으로 취소돼 착수 보상 16,000원이 정산됐어요.",
    createdAt: dayAt(-52, 10, 30),
    read: true,
    targetId: "work-206",
  },
  {
    id: "noti-9",
    type: "REVIEW_RECEIVED",
    title: "후기가 도착했어요",
    body: "가게 로고 디자인, 공룡카페 사장님이 후기를 남겼어요. 프로필에 쌓였어요.",
    createdAt: dayAt(-21, 15, 20),
    read: true,
    targetId: "work-201",
  },
];

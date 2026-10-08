const DAY_MS = 86_400_000;

/** "2026-09-29" → "9월 29일". 시간대에 따라 하루가 밀리지 않게 글자에서 바로 읽는다 */
export function formatMonthDay(isoDate: string): string {
  const [, month, day] = isoDate.split("-").map(Number);
  return `${month}월 ${day}일`;
}

const startOfDay = (date: Date) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();

/** 오늘이면 0, 어제면 1, 그 전날이면 2 … */
export function daysAgo(isoDateTime: string, now = new Date()): number {
  return Math.round((startOfDay(now) - startOfDay(new Date(isoDateTime))) / DAY_MS);
}

const monthDay = (date: Date) => `${date.getMonth() + 1}월 ${date.getDate()}일`;
const hourMinute = (date: Date) =>
  `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;

/** 채팅 목록 시간: 오늘 = 14:22, 어제 = 어제, 그 전 = 9월 12일 */
export function formatChatTime(isoDateTime: string, now = new Date()): string {
  const date = new Date(isoDateTime);
  const days = daysAgo(isoDateTime, now);
  if (days <= 0) return hourMinute(date);
  if (days === 1) return "어제";
  return monthDay(date);
}

/** 알림 시간: 1시간 안 = 10분 전, 오늘 = 2시간 전, 어제 = 어제 14:22, 그 전 = 9월 20일 */
export function formatNotificationTime(isoDateTime: string, now = new Date()): string {
  const date = new Date(isoDateTime);
  const days = daysAgo(isoDateTime, now);
  if (days <= 0) {
    const minutes = Math.max(1, Math.floor((now.getTime() - date.getTime()) / 60_000));
    return minutes < 60 ? `${minutes}분 전` : `${Math.floor(minutes / 60)}시간 전`;
  }
  if (days === 1) return `어제 ${hourMinute(date)}`;
  return monthDay(date);
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** "2026-09-27" → "9월 27일 (일)" */
export function formatMonthDayWeekday(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const weekday = WEEKDAYS[new Date(year, month - 1, day).getDay()];
  return `${month}월 ${day}일 (${weekday})`;
}

/** 채팅방 날짜 칸: ISO 시각 → "10월 1일 (목)" */
export function formatDayChip(isoDateTime: string): string {
  const date = new Date(isoDateTime);
  return `${monthDay(date)} (${WEEKDAYS[date.getDay()]})`;
}

/** 오늘 날짜 "2026-10-02" (날짜 입력의 min 값) */
export function todayIsoDate(now = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

/**
 * 서버 시각("2026-10-08T00:30:00+09:00")의 한국 날짜 "2026-10-08". 오프셋(+09:00 · Z)을 읽어 한국 날짜로 바꾸고,
 * 오프셋이 없거나 읽지 못하면 앞 10자(날짜)를 그대로 쓴다
 */
export function koreaDate(dateTime: string): string {
  if (!/(Z|[+-]\d{2}:?\d{2})$/.test(dateTime)) return dateTime.slice(0, 10);
  const time = Date.parse(dateTime);
  if (Number.isNaN(time)) return dateTime.slice(0, 10);
  return new Date(time + 9 * 3_600_000).toISOString().slice(0, 10);
}

/** 서버가 오프셋 없이 주는 UTC 시각을 ISO(Z) 로 ("2026-10-07T15:22:05" → "2026-10-07T15:22:05.000Z"). 읽지 못하면 그대로 */
export function isoOfUtc(utcDateTime: string): string {
  const time = Date.parse(`${utcDateTime.replace(/(\.\d{3})\d+/, "$1").replace(/Z$/, "")}Z`);
  return Number.isNaN(time) ? utcDateTime : new Date(time).toISOString();
}

/** "2026-09-11" → "2026.09.11", "2025-03" → "2025.03" */
export function formatDotDate(isoDate: string): string {
  return isoDate.split("-").join(".");
}

/** "2026-10-02" 에서 days 일 뒤 "2026-10-09" */
export function addDays(isoDate: string, days: number): string {
  const [year, month, dayOfMonth] = isoDate.split("-").map(Number);
  return todayIsoDate(new Date(year, month - 1, dayOfMonth + days));
}

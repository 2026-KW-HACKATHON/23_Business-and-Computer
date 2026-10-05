/*
 * 임시 예시 데이터의 날짜 · 시각. 오늘을 기준으로 세서 언제 열어도 앞뒤가 맞는다.
 * 사장님 · 학생 예시 데이터가 같이 쓴다.
 */

/** 지금에서 minutes 분 전 (ISO 시각) */
export const minutesAgo = (minutes: number) =>
  new Date(Date.now() - minutes * 60_000).toISOString();

/** 어제 hh:mm */
export const yesterdayAt = (hours: number, minutes: number) => {
  const date = new Date();
  date.setDate(date.getDate() - 1);
  date.setHours(hours, minutes, 0, 0);
  return date.toISOString();
};

const isoDate = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;

/** 오늘에서 offset 일 뒤의 날짜 "YYYY-MM-DD" (음수면 전) */
export const day = (offset: number) => {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  return isoDate(date);
};

/** 오늘에서 offset 일 뒤 hh:mm (ISO 시각) */
export const dayAt = (offset: number, hours: number, minutes: number) => {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  date.setHours(hours, minutes, 0, 0);
  return date.toISOString();
};

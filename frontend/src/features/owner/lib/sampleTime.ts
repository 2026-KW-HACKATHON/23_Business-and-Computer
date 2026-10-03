/* 예시 데이터의 시각을 「10분 전」 · 「어제」처럼 보이도록 지금 시각에서 거꾸로 센다 */

export const minutesAgo = (minutes: number) =>
  new Date(Date.now() - minutes * 60_000).toISOString();

/** 어제 hh:mm */
export const yesterdayAt = (hours: number, minutes: number) => {
  const date = new Date();
  date.setDate(date.getDate() - 1);
  date.setHours(hours, minutes, 0, 0);
  return date.toISOString();
};

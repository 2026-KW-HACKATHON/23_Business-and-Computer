/*
 * 임시 예시 데이터의 날짜. 오늘을 기준으로 세서 언제 열어도 앞뒤가 맞는다.
 * 사장님 · 학생 예시 데이터가 같이 쓴다.
 */

const isoDate = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;

/** 오늘에서 offset 일 뒤의 날짜 "YYYY-MM-DD" (음수면 전) */
export const day = (offset: number) => {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  return isoDate(date);
};

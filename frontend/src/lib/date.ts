/** "2026-09-29" → "9월 29일". 시간대에 따라 하루가 밀리지 않게 글자에서 바로 읽는다 */
export function formatMonthDay(isoDate: string): string {
  const [, month, day] = isoDate.split("-").map(Number);
  return `${month}월 ${day}일`;
}

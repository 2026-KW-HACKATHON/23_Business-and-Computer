/** 받침이 있으면 「이」, 없으면 「가」를 붙인다 (예: 월계분식이 · 광운카페가) */
export function withSubject(word: string): string {
  const last = word.charCodeAt(word.length - 1);
  const isHangul = last >= 0xac00 && last <= 0xd7a3;
  return `${word}${isHangul && (last - 0xac00) % 28 !== 0 ? "이" : "가"}`;
}

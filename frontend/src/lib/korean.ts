/** 받침이 있으면 「이」, 없으면 「가」를 붙인다 (예: 월계분식이 · 광운카페가) */
export function withSubject(word: string): string {
  const last = word.charCodeAt(word.length - 1);
  const isHangul = last >= 0xac00 && last <= 0xd7a3;
  return `${word}${isHangul && (last - 0xac00) % 28 !== 0 ? "이" : "가"}`;
}

/** 「김광운」 → 「김광운 학생」. 이름이 이미 「학생」으로 끝나면(둘러보기의 「데모 학생」) 그대로 */
export function studentTitle(name: string): string {
  return name.endsWith("학생") ? name : `${name} 학생`;
}

/** 「이새빛」 → 「이새빛 사장님」. 이름이 이미 「사장님」으로 끝나면(둘러보기의 「데모 사장님」) 그대로 */
export function ownerTitle(name: string): string {
  return name.endsWith("사장님") ? name : `${name} 사장님`;
}

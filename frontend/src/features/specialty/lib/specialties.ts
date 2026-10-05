import type { SpecialtyCategory } from "../types";

/** 고를 특기가 있는 분류만. 「기타」(specialties: []) 는 가입·제안 화면에서 뺀다 */
export function selectableCategories(categories: SpecialtyCategory[]): SpecialtyCategory[] {
  return categories.filter((category) => category.specialties.length > 0);
}

/** 분류 이름과 특기 이름으로 특기를 찾는다 (홈 제안 예시처럼 이름만 아는 경우). 없으면 undefined */
export function findSpecialtyByName(
  categories: SpecialtyCategory[],
  categoryName: string,
  specialtyName: string,
): { category: SpecialtyCategory; specialty: { id: number; name: string } } | undefined {
  const category = categories.find((c) => c.name === categoryName);
  const specialty = category?.specialties.find((s) => s.name === specialtyName);
  return category && specialty ? { category, specialty } : undefined;
}

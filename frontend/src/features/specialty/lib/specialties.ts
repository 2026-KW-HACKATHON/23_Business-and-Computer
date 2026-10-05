import type { SpecialtyCategory } from "../types";

/**
 * 고를 특기가 있는 분류만. 특기가 없는 분류(specialties: [])는 가입·제안 화면에서 뺀다.
 * 「기타」는 백엔드가 특기 「기타」 1개를 넣으면 이 규칙대로 자동으로 보인다.
 */
export function selectableCategories(categories: SpecialtyCategory[]): SpecialtyCategory[] {
  return categories.filter((category) => category.specialties.length > 0);
}

/**
 * 분류를 고르는 것만으로 정해지는 특기. 「기타」처럼 특기가 딱 1개이고 이름이 분류 이름과 같으면
 * 그 특기를, 아니면 undefined. 이런 분류는 할 일 칩을 따로 보여주지 않는다.
 */
export function implicitSpecialty(
  category: SpecialtyCategory,
): { id: number; name: string } | undefined {
  const [only, ...rest] = category.specialties;
  return only && rest.length === 0 && only.name === category.name ? only : undefined;
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

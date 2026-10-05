import { apiData } from "../../../api/client";
import type { SpecialtyCategory } from "../types";

/**
 * GET /specialties — 대분류별 특기 목록 (data 는 배열).
 * 「기타」처럼 비어 있는 분류도 그대로 돌려준다. 거를지는 화면이 정한다 (`selectableCategories`).
 */
export async function fetchSpecialties(): Promise<SpecialtyCategory[]> {
  return (await apiData<SpecialtyCategory[] | undefined>("/specialties")) ?? [];
}

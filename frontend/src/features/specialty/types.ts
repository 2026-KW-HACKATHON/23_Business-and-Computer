/** GET /specialties 의 대분류 하나. 백엔드가 id 순서로 준다 */
export interface SpecialtyCategory {
  id: number;
  name: string;
  /** 「기타」처럼 고를 특기가 없는 분류는 빈 배열 */
  specialties: { id: number; name: string }[];
}

/** 특기 목록 불러오기 상태 */
export type SpecialtyLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; categories: SpecialtyCategory[] };

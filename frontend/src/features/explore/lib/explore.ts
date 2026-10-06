import { ApiError } from "../../../api/client";
import type { CardKind } from "../../../components";
import type { Field } from "../../../types/field";
import type { SpecialtyCategory } from "../../specialty";
import { fetchExplore } from "../api/exploreApi";
import type {
  ExploreItem,
  ExplorePage,
  ExploreQuery,
  ExploreSort,
  ExploreSpecialtyCategory,
  ExploreType,
  JobStatus,
} from "../api/exploreApi";

/** 정렬 버튼 · 정렬 시트 글자 */
export const SORT_LABEL: Record<ExploreSort, string> = {
  LATEST: "최신순",
  OLDEST: "오래된순",
  LIKES: "공감 많은 순",
};

/** 종류 탭 → 서버 type */
export function exploreType(kind: CardKind): ExploreType {
  if (kind === "proposal") return "PROPOSAL";
  if (kind === "request") return "JOB";
  return "ALL";
}

/** 공감 많은 순은 제안 탭에서만. 다른 탭이면 최신순 */
export function sortForKind(kind: CardKind, sort: ExploreSort): ExploreSort {
  return sort === "LIKES" && kind !== "proposal" ? "LATEST" : sort;
}

/** 화면 분야(Field) → 서버 대분류 id. 이름이 같은 대분류가 없으면 undefined */
export function categoryIdOf(field: Field, categories: SpecialtyCategory[]): number | undefined {
  return categories.find((category) => category.name === field)?.id;
}

/** 뱃지로 보일 대분류 이름 (겹치지 않게) */
export function categoryNames(categories: ExploreSpecialtyCategory[]): string[] {
  return [...new Set(categories.map((category) => category.name))];
}

/** 의뢰 카드의 굵은 상태 글자 */
export function jobStatusLabel(status: JobStatus): string {
  switch (status) {
    case "OPEN":
      return "모집 중";
    case "AWAITING_START":
    case "MATCHED":
      return "진행 중";
    case "CLOSED":
      return "완료";
    case "CANCELLED":
      return "성사되지 않음";
  }
}

/** 목록 key. 제안과 의뢰의 id 는 서로 겹칠 수 있다 */
export function exploreItemKey(item: ExploreItem): string {
  return item.type === "PROPOSAL" ? `proposal-${item.proposalId}` : `job-${item.jobId}`;
}

/** 검색어가 제목이나 가게 이름에 있는지. 빈 검색어는 모두 */
export function matchesKeyword(item: ExploreItem, keyword: string): boolean {
  return keyword === "" || item.title.includes(keyword) || item.storeName.includes(keyword);
}

/** GET /explore 결과 */
export type ExploreLoadResult =
  | { status: "loaded"; page: ExplorePage }
  /** apiData 가 /refresh 로 한 번 다시 시도한 뒤에도 401 */
  | { status: "unauthorized" }
  | { status: "error" };

export async function loadExplorePage(query: ExploreQuery): Promise<ExploreLoadResult> {
  try {
    return { status: "loaded", page: await fetchExplore(query) };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "error" };
  }
}

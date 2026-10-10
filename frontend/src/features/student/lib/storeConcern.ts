import type { ExploreStore } from "../types";

// 고민을 올리거나 고친 시각. 고민이 없으면 0
function concernTime(store: ExploreStore): number {
  return store.concern ? Date.parse(store.concern.updatedAt) || 0 : 0;
}

/**
 * 고민이 있는 가게를 위로 올린다. 고민 있는 가게끼리는 최근에 올리거나 고친 순서이고,
 * 고민 없는 가게는 받은 순서(등록순) 그대로다.
 */
export function sortStoresByConcern(stores: ExploreStore[]): ExploreStore[] {
  const withConcern = stores
    .filter((store) => store.concern)
    .sort((a, b) => concernTime(b) - concernTime(a));
  return [...withConcern, ...stores.filter((store) => !store.concern)];
}

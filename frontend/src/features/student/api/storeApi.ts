import { apiData } from "../../../api/client";
import type { ExploreStore } from "../types";

interface StoreExploreResponse {
  items: {
    storeName: string;
    profileImageUrl: string | null;
    businessCategory: { id: number; name: string };
    storeAddress: string;
    ownerProfileId: number;
    createdAt: string;
    /** 해결되지 않은 가게 고민. 없으면 null */
    concern?: {
      title: string;
      description: string | null;
      specialtyCategory: { id: number; name: string } | null;
      updatedAt: string;
    } | null;
  }[];
  nextCursor: string | null;
  hasNext: boolean;
}

/** 백엔드 한 번에 최대 100개 (StoreExploreRequest) */
const PAGE_SIZE = 100;
/** 커서가 끝나지 않는 이상 상황에서 멈추는 안전장치 (100 × 20 = 2,000곳) */
const MAX_PAGES = 20;

/**
 * GET /explore/stores 를 hasNext 가 끝날 때까지 모두 불러온다 (학생만).
 * 이름 검색 파라미터가 없어서, 업종·이름 거르기는 화면에서 한다. 등록순(OLDEST).
 */
export async function fetchAllExploreStores(): Promise<ExploreStore[]> {
  const stores: ExploreStore[] = [];
  let cursor: string | null = null;
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const params = new URLSearchParams({ sort: "OLDEST", size: String(PAGE_SIZE) });
    if (cursor) params.set("cursor", cursor);
    const data: StoreExploreResponse | undefined = await apiData<StoreExploreResponse | undefined>(
      `/explore/stores?${params.toString()}`,
    );
    for (const item of data?.items ?? []) {
      stores.push({
        ownerProfileId: item.ownerProfileId,
        name: item.storeName,
        category: item.businessCategory.name,
        address: item.storeAddress,
        photo: item.profileImageUrl,
        concern: item.concern
          ? {
              title: item.concern.title,
              description: item.concern.description,
              category: item.concern.specialtyCategory?.name ?? null,
              updatedAt: item.concern.updatedAt,
            }
          : null,
      });
    }
    if (!data?.hasNext || !data.nextCursor) break;
    cursor = data.nextCursor;
  }
  return stores;
}

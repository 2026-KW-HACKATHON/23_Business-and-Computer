import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { CardKind } from "../../../components";
import type { Field } from "../../../types/field";
import { useSpecialties } from "../../specialty";
import type { ExploreItem, ExploreProposalCard, ExploreSort, ExploreType } from "../api/exploreApi";
import { categoryIdOf, exploreItemKey, exploreType, loadExplorePage, sortForKind } from "../lib/explore";

export interface ExploreFilter {
  type: ExploreType;
  categoryId?: number;
  sort: ExploreSort;
  /** 한 쪽의 카드 수. 없으면 서버 기본 20 */
  size?: number;
}

/** 탐색 목록. items 는 지금까지 불러온 쪽을 이어 붙인 것 */
export interface ExploreFeed {
  /** 첫 쪽 상태 */
  status: "loading" | "error" | "loaded";
  items: ExploreItem[];
  hasNext: boolean;
  /** 다음 쪽 상태. error 면 loadMore 로 같은 쪽을 다시 부른다 */
  more: "idle" | "loading" | "error";
  loadMore: () => void;
  /** 첫 쪽부터 다시 */
  reload: () => void;
}

interface FeedState {
  /** 어느 조건 · 다시 시도 횟수의 목록인지 */
  token: string;
  status: "error" | "loaded";
  items: ExploreItem[];
  nextCursor: string | null;
  hasNext: boolean;
  more: ExploreFeed["more"];
}

const noop = () => {};

/**
 * GET /explore 를 커서로 이어 부른다. 조건이 바뀌면 첫 쪽부터 다시 부르고, 지난 조건의 응답은 버린다.
 * filter 가 null 이면 부르지 않고 loading 이다. 401 은 /login 으로 보낸다.
 */
export function useExplore(filter: ExploreFilter | null): ExploreFeed {
  const navigate = useNavigate();
  const type = filter?.type;
  const categoryId = filter?.categoryId;
  const sort = filter?.sort;
  const size = filter?.size;
  const [retry, setRetry] = useState(0);
  const token = type && sort ? `${type}:${categoryId ?? ""}:${sort}:${size ?? ""}#${retry}` : "";
  const [feed, setFeed] = useState<FeedState>();
  // 지금 화면이 보여 주는 token. 다음 쪽 응답이 늦게 오면 이것과 비교해 버린다
  const live = useRef("");
  // 다음 쪽을 부르는 중인 token (두 번 부르지 않게)
  const moreFor = useRef<string | null>(null);

  useEffect(() => {
    if (!type || !sort) return;
    const liveRef = live;
    liveRef.current = token;
    let active = true;
    void loadExplorePage({ type, categoryId, sort, size }).then((result) => {
      if (!active) return;
      if (result.status === "unauthorized") {
        navigate("/login", { replace: true });
        return;
      }
      setFeed(
        result.status === "loaded"
          ? { token, status: "loaded", more: "idle", ...result.page }
          : { token, status: "error", more: "idle", items: [], nextCursor: null, hasNext: false },
      );
    });
    return () => {
      active = false;
      if (liveRef.current === token) liveRef.current = "";
    };
  }, [type, categoryId, sort, size, token, navigate]);

  const current = feed?.token === token ? feed : undefined;
  const nextCursor = current?.status === "loaded" && current.hasNext ? current.nextCursor : null;

  const loadMore = useCallback(() => {
    if (!type || !sort || !nextCursor || moreFor.current === token) return;
    moreFor.current = token;
    setFeed((prev) => (prev?.token === token ? { ...prev, more: "loading" } : prev));
    void loadExplorePage({ type, categoryId, sort, size, cursor: nextCursor }).then((result) => {
      if (moreFor.current === token) moreFor.current = null;
      if (live.current !== token) return;
      if (result.status === "unauthorized") {
        navigate("/login", { replace: true });
        return;
      }
      setFeed((prev) => {
        if (prev?.token !== token || prev.nextCursor !== nextCursor) return prev;
        if (result.status !== "loaded") return { ...prev, more: "error" };
        const seen = new Set(prev.items.map(exploreItemKey));
        return {
          ...prev,
          items: [...prev.items, ...result.page.items.filter((item) => !seen.has(exploreItemKey(item)))],
          nextCursor: result.page.nextCursor,
          hasNext: result.page.hasNext,
          more: "idle",
        };
      });
    });
  }, [type, categoryId, sort, size, token, nextCursor, navigate]);

  const reload = useCallback(() => setRetry((n) => n + 1), []);

  if (!current) {
    return { status: "loading", items: [], hasNext: false, more: "idle", loadMore: noop, reload };
  }
  return {
    status: current.status,
    items: current.items,
    hasNext: current.hasNext,
    more: current.more,
    loadMore,
    reload,
  };
}

/**
 * 탐색 화면의 종류 탭 · 분야 · 정렬로 목록을 부른다. 분야는 GET /specialties 에서 이름이 같은
 * 대분류 id 로 바꾼다. 특기 목록을 불러오는 중이면 loading, 실패면 error (다시 시도는 특기 목록부터),
 * 서버에 그 이름의 대분류가 없으면 빈 목록이다. 공감 많은 순은 제안 탭에서만 보낸다.
 */
export function useExploreFeed({
  kind,
  field,
  sort,
}: {
  kind: CardKind;
  field: Field | null;
  sort: ExploreSort;
}): ExploreFeed {
  const { load: specialties, reload: reloadSpecialties } = useSpecialties();
  const type = exploreType(kind);
  const sent = sortForKind(kind, sort);
  const categoryId =
    field !== null && specialties.status === "loaded"
      ? categoryIdOf(field, specialties.categories)
      : undefined;
  const unknownField = field !== null && specialties.status === "loaded" && categoryId === undefined;
  const ready = field === null || categoryId !== undefined;
  const feed = useExplore(ready ? { type, categoryId, sort: sent } : null);

  if (field !== null && specialties.status === "error") {
    return { ...feed, status: "error", items: [], hasNext: false, more: "idle", reload: reloadSpecialties };
  }
  if (unknownField) {
    return { status: "loaded", items: [], hasNext: false, more: "idle", loadMore: noop, reload: noop };
  }
  return feed;
}

/**
 * 공감 많은 제안 앞의 size 개 (GET /explore?type=PROPOSAL&sort=LIKES). 첫 쪽만 부른다.
 * 401 은 /login 으로 보낸다.
 */
export function usePopularProposals(size: number): {
  status: ExploreFeed["status"];
  proposals: ExploreProposalCard[];
} {
  const { status, items } = useExplore({ type: "PROPOSAL", sort: "LIKES", size });
  return {
    status,
    proposals: items.filter((item): item is ExploreProposalCard => item.type === "PROPOSAL"),
  };
}

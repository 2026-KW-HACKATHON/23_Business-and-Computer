import { useCallback, useEffect, useState } from "react";
import { fetchAllExploreStores } from "../api/storeApi";
import type { ExploreStore } from "../types";

export type ExploreStoresLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; stores: ExploreStore[] };

/**
 * 가게 탐색 · 제안 보내기 1/4 의 가게 목록 (모든 페이지). 실패하면 reload 로 다시 불러온다.
 * 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useExploreStores(): { load: ExploreStoresLoad; reload: () => void } {
  const [load, setLoad] = useState<ExploreStoresLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    fetchAllExploreStores().then(
      (stores) => {
        if (active) setLoad({ status: "loaded", stores });
      },
      () => {
        if (active) setLoad({ status: "error" });
      },
    );
    return () => {
      active = false;
    };
  }, [request]);

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  return { load, reload };
}

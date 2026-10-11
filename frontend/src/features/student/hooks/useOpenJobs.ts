import { useCallback, useEffect, useState } from "react";
import { fetchExplore } from "../../explore/api/exploreApi";
import type { ExploreJobCard } from "../../explore/api/exploreApi";
import { useCachedListRevision } from "../../../hooks/useCachedListRevision";

export type OpenJobsLoad =
  | { status: "loading" }
  | { status: "loaded"; jobs: ExploreJobCard[] }
  | { status: "error" };

/** 한 번에 받는 의뢰 수 (서버 허용 최대) · 끝까지 넘겨 받을 쪽 수 상한 */
const PAGE_SIZE = 100;
const MAX_PAGES = 5;

/** GET /explore (의뢰, 최신순) 를 끝까지 넘겨 받는다. 「나에게 맞는 일 찾기」 결과에서 쓴다 (ADR 0072) */
async function fetchAllJobs(): Promise<ExploreJobCard[]> {
  const jobs: ExploreJobCard[] = [];
  let cursor: string | undefined;
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const result = await fetchExplore({ type: "JOB", sort: "LATEST", cursor, size: PAGE_SIZE });
    jobs.push(...result.items.filter((item): item is ExploreJobCard => item.type === "JOB"));
    if (!result.hasNext || !result.nextCursor) break;
    cursor = result.nextCursor;
  }
  return jobs;
}

export function useOpenJobs(): { load: OpenJobsLoad; reload: () => void } {
  const [load, setLoad] = useState<OpenJobsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  // 기억해 둔 목록이 뒤에서 새로 바뀌면 다시 그린다 (ADR 0077)
  const revision = useCachedListRevision();
  useEffect(() => {
    let active = true;
    fetchAllJobs().then(
      (jobs) => {
        if (active) setLoad({ status: "loaded", jobs });
      },
      () => {
        if (active) setLoad({ status: "error" });
      },
    );
    return () => {
      active = false;
    };
  }, [request, revision]);

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  return { load, reload };
}

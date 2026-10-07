import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadFinishedJobs } from "../lib/finishedJobs";
import type { FinishedJob, FinishedJobsOptions } from "../lib/finishedJobs";

export type FinishedJobsLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; jobs: FinishedJob[] };

/**
 * 끝난 내 작업 (GET /settlements 에서). 홈 끝난 일 · 내 활동 완료 · 내 작업물이 쓴다.
 * 401 은 /login 으로 보낸다 (그동안은 loading). reload 로 다시 불러오고, 지난 응답은 버린다.
 */
export function useFinishedJobs({ details = false, reviews = false, files = false }: FinishedJobsOptions = {}): {
  load: FinishedJobsLoad;
  reload: () => void;
} {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ key: string; load: FinishedJobsLoad }>();
  const current = `${details}:${reviews}:${files}:${request}`;

  useEffect(() => {
    let active = true;
    void loadFinishedJobs({ details, reviews, files }).then((loaded) => {
      if (!active) return;
      if (loaded.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else {
        setResult({ key: current, load: loaded });
      }
    });
    return () => {
      active = false;
    };
  }, [details, reviews, files, current, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);
  return { load: result?.key === current ? result.load : { status: "loading" }, reload };
}

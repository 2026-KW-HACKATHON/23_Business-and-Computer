import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadFinishedWork, loadReceivedReview } from "../lib/finishedWork";
import type { FinishedLoadResult, FinishedWork, ReceivedReview } from "../lib/finishedWork";

export type FinishedLoad<T> =
  | { status: "loading" }
  | { status: "error" }
  | { status: "notFound" }
  | { status: "loaded"; data: T };

/**
 * 끝난 작업 하나를 불러온다. 401 은 /login 으로 보낸다 (그동안은 loading).
 * 작업이나 다시 시도가 바뀌면 응답이 올 때까지 loading 이고, 지난 응답은 버린다.
 */
function useFinishedLoad<T>(
  jobId: number,
  load: (jobId: number) => Promise<FinishedLoadResult<T>>,
): { load: FinishedLoad<T>; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ key: string; load: FinishedLoad<T> }>();
  const current = `${jobId}:${request}`;

  useEffect(() => {
    let active = true;
    void load(jobId).then((loaded) => {
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
  }, [jobId, current, load, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);
  return { load: result?.key === current ? result.load : { status: "loading" }, reload };
}

/** 완료된 내 작업의 결과물 · 가게 이름 · 받은 후기 (내 결과물 보기). 끝나지 않았으면 notFound */
export function useFinishedWork(jobId: number): { load: FinishedLoad<FinishedWork>; reload: () => void } {
  return useFinishedLoad(jobId, loadFinishedWork);
}

/** 완료된 내 작업에서 받은 후기 (받은 후기 보기). 아직 후기가 없으면 notFound */
export function useReceivedReview(jobId: number): { load: FinishedLoad<ReceivedReview>; reload: () => void } {
  return useFinishedLoad(jobId, loadReceivedReview);
}

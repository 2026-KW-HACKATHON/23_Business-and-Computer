import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadSubmissionHistory } from "../lib/latestSubmission";
import type { LatestSubmission } from "../lib/latestSubmission";

export type SubmissionHistoryLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "notFound" }
  | { status: "loaded"; submissions: LatestSubmission[] };

/**
 * 내가 맡은 의뢰에 낸 모든 초안 · 수정안과 각 수정 요청 (GET /jobs/{id}/submissions). 작업 이력 · 지난 서류 화면이
 * 쓴다. 401 은 /login 으로 보낸다 (그동안은 loading). 의뢰나 다시 시도가 바뀌면 응답이 올 때까지 loading 이다.
 */
export function useSubmissionHistory(jobId: number): { load: SubmissionHistoryLoad; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ key: string; load: SubmissionHistoryLoad }>();
  const current = `${jobId}:${request}`;

  useEffect(() => {
    let active = true;
    void loadSubmissionHistory(jobId).then((loaded) => {
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
  }, [jobId, current, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);
  const load: SubmissionHistoryLoad = result?.key === current ? result.load : { status: "loading" };
  return { load, reload };
}

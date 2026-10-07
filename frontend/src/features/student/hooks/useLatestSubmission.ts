import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadLatestSubmission } from "../lib/latestSubmission";
import type { LatestSubmission } from "../lib/latestSubmission";

export type LatestSubmissionLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "notFound" }
  | { status: "loaded"; submission: LatestSubmission };

/**
 * 내가 맡은 의뢰에 마지막으로 낸 초안 · 수정안 (GET /jobs/{id}/submissions/latest).
 * 수정 요청 확인 · 제출한 결과물 화면이 쓴다. 401 은 /login 으로 보낸다 (그동안은 loading).
 * 의뢰나 다시 시도가 바뀌면 응답이 올 때까지 loading 이고, 지난 응답은 버린다.
 */
export function useLatestSubmission(jobId: number): { load: LatestSubmissionLoad; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ key: string; load: LatestSubmissionLoad }>();
  const current = `${jobId}:${request}`;

  useEffect(() => {
    let active = true;
    void loadLatestSubmission(jobId).then((loaded) => {
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
  const load: LatestSubmissionLoad = result?.key === current ? result.load : { status: "loading" };
  return { load, reload };
}

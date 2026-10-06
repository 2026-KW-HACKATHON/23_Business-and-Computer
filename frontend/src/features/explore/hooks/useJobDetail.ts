import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { JobDetail } from "../api/jobApi";
import { loadJobDetail, parseJobId } from "../lib/jobDetail";

export type JobDetailLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "notFound" }
  | { status: "loaded"; job: JobDetail };

/**
 * 의뢰 하나 (GET /jobs/{id}). 주소의 id 가 숫자가 아니면 요청하지 않고 notFound.
 * 401 은 /login 으로 보낸다. 다른 id 로 바뀌거나 reload 하면 응답이 올 때까지 loading 이다.
 */
export function useJobDetail(jobId: string | undefined): { load: JobDetailLoad; reload: () => void } {
  const navigate = useNavigate();
  const id = parseJobId(jobId);
  const [request, setRequest] = useState(0);
  // 어느 요청(id · 다시 시도 횟수)의 결과인지 함께 둬서 지난 결과를 보이지 않는다
  const key = `${id}:${request}`;
  const [result, setResult] = useState<{ key: string; load: JobDetailLoad }>();

  useEffect(() => {
    if (id === undefined) return;
    let active = true;
    void loadJobDetail(id).then((loaded) => {
      if (!active) return;
      if (loaded.status === "unauthorized") {
        navigate("/login", { replace: true });
        return;
      }
      setResult({ key, load: loaded.status === "loaded" ? loaded : { status: loaded.status } });
    });
    return () => {
      active = false;
    };
  }, [id, key, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);

  const load: JobDetailLoad =
    id === undefined ? { status: "notFound" } : result?.key === key ? result.load : { status: "loading" };
  return { load, reload };
}

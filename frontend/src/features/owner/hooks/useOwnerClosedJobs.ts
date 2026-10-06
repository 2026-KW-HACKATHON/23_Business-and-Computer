import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadOwnerClosedJobs } from "../lib/closedJobs";
import type { OwnerClosedJob } from "../lib/closedJobs";

export type OwnerClosedJobsLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; jobs: OwnerClosedJob[] };

/**
 * 끝난 내 의뢰 (GET /me/jobs?status=CLOSED). 내 활동 완료 탭 · 홈 끝난 일 · 성사되지 않은 작업 상세가 쓴다.
 * 실패하면 reload 로 다시 불러온다. 401 은 /login 으로 보낸다 (그동안은 loading).
 * 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useOwnerClosedJobs(): { load: OwnerClosedJobsLoad; reload: () => void } {
  const navigate = useNavigate();
  const [load, setLoad] = useState<OwnerClosedJobsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    void loadOwnerClosedJobs().then((result) => {
      if (!active) return;
      if (result.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else {
        setLoad(result);
      }
    });
    return () => {
      active = false;
    };
  }, [request, navigate]);

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  return { load, reload };
}

import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadOwnerProgressJobs } from "../lib/progressJobs";
import type { OwnerProgressJob } from "../lib/progressJobs";

export type OwnerProgressJobsLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; jobs: OwnerProgressJob[] };

/**
 * 학생이 맡아 진행 중인 내 의뢰 (GET /me/jobs?status=MATCHED). 내 활동 · 홈 · 작업 확인 화면이 함께 쓴다.
 * 실패하면 reload 로 다시 불러온다. 401 은 /login 으로 보낸다 (그동안은 loading).
 * 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useOwnerProgressJobs(): { load: OwnerProgressJobsLoad; reload: () => void } {
  const navigate = useNavigate();
  const [load, setLoad] = useState<OwnerProgressJobsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    void loadOwnerProgressJobs().then((result) => {
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

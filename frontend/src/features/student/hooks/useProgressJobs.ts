import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadProgressJobs } from "../lib/progressJobs";
import type { ProgressJob } from "../lib/progressJobs";

export type ProgressJobsLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; jobs: ProgressJob[] };

/**
 * 나와 매칭된 진행 중 작업 (GET /me/jobs?status=MATCHED). 내 활동 · 홈 · 제출 화면이 함께 쓴다.
 * 가게 주소를 그리는 화면만 storeAddress 를 켠다 (의뢰마다 GET /jobs/{id}).
 * 실패하면 reload 로 다시 불러온다. 401 은 /login 으로 보낸다 (그동안은 loading).
 * 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useProgressJobs({ storeAddress = false }: { storeAddress?: boolean } = {}): {
  load: ProgressJobsLoad;
  reload: () => void;
} {
  const navigate = useNavigate();
  const [load, setLoad] = useState<ProgressJobsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    void loadProgressJobs({ storeAddress }).then((result) => {
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
  }, [request, storeAddress, navigate]);

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  return { load, reload };
}

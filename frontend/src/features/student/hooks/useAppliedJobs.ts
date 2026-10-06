import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { landingPath } from "../../auth";
import { loadAppliedJobs } from "../lib/appliedJobs";
import type { AppliedJob } from "../lib/appliedJobs";

export type AppliedJobsLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; jobs: AppliedJob[] };

/**
 * 내가 지원한 의뢰 (GET /me/job-applications, 최신 지원순). 내 활동 · 홈 · 내 정보가 함께 쓴다.
 * 실패하면 reload 로 다시 불러온다. 401 은 /login, 403 JOB_APPLICATION_403_LIST_STUDENT 는 알림 뒤
 * landingPath() 로 보낸다 (그동안은 loading). 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useAppliedJobs(): { load: AppliedJobsLoad; reload: () => void } {
  const navigate = useNavigate();
  const [load, setLoad] = useState<AppliedJobsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    void loadAppliedJobs().then((result) => {
      if (!active) return;
      if (result.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (result.status === "forbidden") {
        window.alert("학생만 지원한 의뢰를 볼 수 있어요");
        navigate(landingPath(), { replace: true });
      } else {
        setLoad(result.status === "loaded" ? result : { status: "error" });
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

import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { ApplicationPlan } from "../../../types/workPlan";
import { loadAssignedWork } from "../lib/progressJobs";
import type { OwnerProgressJob } from "../lib/progressJobs";

export type AssignedWorkLoad =
  /** 학생이 작업 중인 의뢰가 아니라 부르지 않음 */
  | { status: "idle" }
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; work?: OwnerProgressJob; plan?: ApplicationPlan };

/**
 * 보낸 의뢰 상세에서 학생이 작업 중일 때의 맡은 학생 · 단계 · 지원서. jobId 가 없으면 부르지 않는다.
 * 401 은 /login 으로 보낸다. 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useAssignedWork(jobId: number | undefined): { load: AssignedWorkLoad; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  // 받은 답과 그 답을 부른 의뢰 · 횟수. 지금 의뢰 · 횟수의 답이 아니면 불러오는 중이다
  const [answer, setAnswer] = useState<{ key: string; load: AssignedWorkLoad }>();
  const key = `${jobId}:${request}`;

  useEffect(() => {
    if (jobId === undefined) return;
    let active = true;
    void loadAssignedWork(jobId).then((result) => {
      if (!active) return;
      if (result.status === "unauthorized") navigate("/login", { replace: true });
      else setAnswer({ key: `${jobId}:${request}`, load: result });
    });
    return () => {
      active = false;
    };
  }, [jobId, request, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);
  const load: AssignedWorkLoad =
    jobId === undefined ? { status: "idle" } : answer?.key === key ? answer.load : { status: "loading" };

  return { load, reload };
}

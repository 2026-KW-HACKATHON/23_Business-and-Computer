import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../../../api/client";
import { fetchWorkHistory } from "../api/chatApi";
import type { ChatWorkHistoryData } from "../api/chatApi";

export type WorkHistoryLoad =
  | { status: "loading" }
  | { status: "error" }
  /** 404 (작업 · 채팅방 없음) · 403 (그 작업의 사장님 · 맡은 학생이 아님) */
  | { status: "notFound" }
  | { status: "loaded"; history: ChatWorkHistoryData };

type WorkHistoryResult = WorkHistoryLoad | { status: "unauthorized" };

/** 작업 이력을 불러온다. 401 · 403 · 404 만 따로 알리고 나머지는 error (다시 시도) */
async function loadWorkHistory(jobId: number): Promise<WorkHistoryResult> {
  try {
    return { status: "loaded", history: await fetchWorkHistory(jobId) };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403 || error.status === 404) return { status: "notFound" };
    }
    return { status: "error" };
  }
}

/**
 * 채팅 작업 카드의 작업 이력 (GET /jobs/{jobId}/work-history). 그 작업의 채팅방 · 모든 초안 · 수정안과 각 수정 요청 ·
 * 후기 여부를 요청 하나로 받는다. 401 은 /login 으로 보낸다 (그동안은 loading). jobId 가 없으면 부르지 않고
 * loading 이다. 작업이나 다시 시도가 바뀌면 응답이 올 때까지 loading 이다.
 */
export function useWorkHistory(jobId: number | undefined): { load: WorkHistoryLoad; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ key: string; load: WorkHistoryLoad }>();
  const current = `${jobId}:${request}`;

  useEffect(() => {
    if (jobId === undefined) return;
    let active = true;
    void loadWorkHistory(jobId).then((loaded) => {
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
  const load: WorkHistoryLoad = result?.key === current ? result.load : { status: "loading" };
  return { load, reload };
}

import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadOwnerClosedJobs } from "../lib/closedJobs";
import type { OwnerClosedJob } from "../lib/closedJobs";
import type { PaymentSummary } from "../types";
import { useCachedListRevision } from "../../../hooks/useCachedListRevision";

export type OwnerClosedJobsLoad =
  | { status: "loading" }
  | { status: "error" }
  /** paymentSummary 는 완료 탭 위 결제 요약 (GET /payments). 못 불러오면 없음 */
  | { status: "loaded"; jobs: OwnerClosedJob[]; paymentSummary?: PaymentSummary };

/**
 * 끝난 내 의뢰 (GET /me/jobs?status=CLOSED). 내 활동 완료 탭 · 작업 이력 · 채팅 · 성사되지 않은 작업 상세가 쓴다.
 * 실패하면 reload 로 다시 불러온다. 401 은 /login 으로 보낸다 (그동안은 loading).
 * 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useOwnerClosedJobs(): { load: OwnerClosedJobsLoad; reload: () => void } {
  const navigate = useNavigate();
  const [load, setLoad] = useState<OwnerClosedJobsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  // 기억해 둔 목록이 뒤에서 새로 바뀌면 다시 그린다 (ADR 0077)
  const revision = useCachedListRevision();
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
  }, [request, navigate, revision]);

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  return { load, reload };
}

import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { landingPath } from "../../auth";
import { loadSettlementHistory } from "../lib/settlements";
import type { SettlementHistory } from "../lib/settlements";

export type SettlementHistoryLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; data: SettlementHistory };

/**
 * 정산 내역 (GET /settlements). 정산 내역 화면과 내 활동 › 완료의 요약이 쓴다. reload 로 다시 불러온다.
 * 401 은 /login, 403(학생이 아님)은 알림 뒤 landingPath() 로 보낸다. 지난 응답은 버린다.
 */
export function useSettlementHistory(): { load: SettlementHistoryLoad; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ request: number; load: SettlementHistoryLoad }>();

  useEffect(() => {
    let active = true;
    void loadSettlementHistory().then((loaded) => {
      if (!active) return;
      if (loaded.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (loaded.status === "forbidden") {
        window.alert("학생만 정산 내역을 볼 수 있어요");
        navigate(landingPath(), { replace: true });
      } else {
        setResult({ request, load: loaded });
      }
    });
    return () => {
      active = false;
    };
  }, [request, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);
  const load: SettlementHistoryLoad = result?.request === request ? result.load : { status: "loading" };
  return { load, reload };
}

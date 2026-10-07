import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { landingPath } from "../../auth";
import { loadOwnerPaymentHistory } from "../lib/paymentHistory";
import type { OwnerPaymentHistory } from "../lib/paymentHistory";

export type OwnerPaymentHistoryLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; data: OwnerPaymentHistory };

/**
 * 결제 내역 (GET /payments). 결제 내역 화면이 쓴다. reload 로 다시 불러온다.
 * 401 은 /login, 403(사장님이 아님)은 알림 뒤 landingPath() 로 보낸다. 지난 응답은 버린다.
 */
export function useOwnerPaymentHistory(): { load: OwnerPaymentHistoryLoad; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ request: number; load: OwnerPaymentHistoryLoad }>();

  useEffect(() => {
    let active = true;
    void loadOwnerPaymentHistory().then((loaded) => {
      if (!active) return;
      if (loaded.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (loaded.status === "forbidden") {
        window.alert("사장님만 결제 내역을 볼 수 있어요");
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
  const load: OwnerPaymentHistoryLoad = result?.request === request ? result.load : { status: "loading" };
  return { load, reload };
}

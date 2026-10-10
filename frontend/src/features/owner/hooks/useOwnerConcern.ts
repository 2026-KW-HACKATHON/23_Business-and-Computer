import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadOwnerConcern } from "../lib/storeConcern";
import type { OwnerConcern } from "../lib/storeConcern";

export type OwnerConcernLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; concern: OwnerConcern | null };

/**
 * 우리 가게의 해결되지 않은 고민 (GET /owners/me/concern). 홈 · 내 정보 · 고민 작성이 쓴다.
 * reload 로 다시 불러온다 (해결한 뒤 등). 401 은 /login, 지난 응답은 버린다.
 */
export function useOwnerConcern(): { load: OwnerConcernLoad; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ request: number; load: OwnerConcernLoad }>();

  useEffect(() => {
    let active = true;
    void loadOwnerConcern().then((loaded) => {
      if (!active) return;
      if (loaded.status === "unauthorized") navigate("/login", { replace: true });
      else setResult({ request, load: loaded });
    });
    return () => {
      active = false;
    };
  }, [request, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);
  const load: OwnerConcernLoad = result?.request === request ? result.load : { status: "loading" };
  return { load, reload };
}

import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { landingPath } from "../../auth";
import { loadOwnerMe } from "../lib/ownerMe";
import type { OwnerMe } from "../lib/ownerMe";

export type OwnerMeLoad = { status: "loading" } | { status: "error" } | { status: "loaded"; data: OwnerMe };

/**
 * 내 정보 (GET /owners/me). 내 정보 · 가게 정보 수정이 쓴다. reload 로 다시 불러온다 (사진을 바꾼 뒤 등).
 * 401 은 /login, 403(사장님이 아님)은 알림 뒤 landingPath() 로 보낸다. 지난 응답은 버린다.
 */
export function useOwnerMe(): { load: OwnerMeLoad; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ request: number; load: OwnerMeLoad }>();

  useEffect(() => {
    let active = true;
    void loadOwnerMe().then((loaded) => {
      if (!active) return;
      if (loaded.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (loaded.status === "forbidden") {
        window.alert("사장님만 내 정보를 볼 수 있어요");
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
  const load: OwnerMeLoad = result?.request === request ? result.load : { status: "loading" };
  return { load, reload };
}

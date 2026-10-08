import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadOwnerHome } from "../lib/ownerHome";
import type { OwnerHomeData } from "../lib/ownerHome";
import { SAMPLE_REQUEST_EXAMPLES } from "../lib/sampleHome";
import type { OwnerHome, OwnerHomeSectionStatus } from "../types";

/** retrying = 불러온 홈을 두고 다시 부르는 중 (실패한 섹션만 로딩으로 보인다) */
type OwnerHomeLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; home: OwnerHomeData; retrying: boolean };

/**
 * 사장님 홈에 그릴 데이터. GET /me/home 한 번으로 확인할 일 · 학생이 작업 중 · 기다리는 중 · 끝난 일과
 * 처음 온 계정인지(firstVisit, ADR 0051)를 함께 받는다 (ADR 0064). 순서 · 자동 완료 날짜도 서버 값 그대로 쓴다.
 * 서버가 null 로 준 섹션만 실패로 보이고, 다시 시도하면 홈 전체를 다시 부른다.
 * 401 은 /login, 403 HOME_403(가입 전)은 /signup/role 로 보낸다 (그동안은 loading). 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useOwnerHome(): OwnerHome {
  const navigate = useNavigate();
  const [load, setLoad] = useState<OwnerHomeLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    void loadOwnerHome().then((result) => {
      if (!active) return;
      if (result.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (result.status === "signupRequired") {
        navigate("/signup/role", { replace: true });
      } else if (result.status === "loaded") {
        setLoad({ status: "loaded", home: result.home, retrying: false });
      } else {
        // 불러온 홈이 있으면 그대로 두고, 실패한 섹션이 다시 「다시 시도」를 보인다
        setLoad((prev) => (prev.status === "loaded" ? { ...prev, retrying: false } : { status: "error" }));
      }
    });
    return () => {
      active = false;
    };
  }, [request, navigate]);

  const reload = useCallback(() => {
    setLoad((prev) => (prev.status === "loaded" ? { ...prev, retrying: true } : { status: "loading" }));
    setRequest((n) => n + 1);
  }, []);

  const home = load.status === "loaded" ? load.home : undefined;
  const sectionStatus = (items: unknown[] | null | undefined): OwnerHomeSectionStatus => {
    if (load.status !== "loaded") return load.status;
    if (items) return "loaded";
    return load.retrying ? "loading" : "error";
  };

  return {
    firstVisit: load.status === "loading" ? undefined : (home?.firstVisit ?? false),
    status: load.status,
    sections: {
      todos: sectionStatus(home?.todos),
      working: sectionStatus(home?.working),
      waiting: sectionStatus(home?.waiting),
      done: sectionStatus(home?.done),
    },
    reload,
    todos: home?.todos ?? [],
    working: home?.working ?? [],
    waiting: home?.waiting ?? [],
    examples: SAMPLE_REQUEST_EXAMPLES,
    done: home?.done ?? [],
  };
}

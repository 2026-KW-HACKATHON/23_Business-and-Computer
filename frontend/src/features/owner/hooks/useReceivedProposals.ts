import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { landingPath } from "../../auth";
import { loadReceivedProposals } from "../lib/receivedProposals";
import type { ReceivedProposal } from "../lib/receivedProposals";

export type ReceivedProposalsLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; proposals: ReceivedProposal[] };

/**
 * 내가 받은 제안 목록 (GET /me/received-proposals, 최신순). 내 활동 · 탐색 · 결과물 화면이 함께 쓴다.
 * 실패하면 reload 로 다시 불러온다. 401 은 /login, 403 PROPOSAL_403_LIST_OWNER · OWNER_403 은
 * 알림 뒤 landingPath() 로 보낸다 (그동안은 loading). 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useReceivedProposals(): { load: ReceivedProposalsLoad; reload: () => void } {
  const navigate = useNavigate();
  const [load, setLoad] = useState<ReceivedProposalsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    void loadReceivedProposals().then((result) => {
      if (!active) return;
      if (result.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (result.status === "forbidden") {
        window.alert("사장님만 받은 제안을 볼 수 있어요");
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

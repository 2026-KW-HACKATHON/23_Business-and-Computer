import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { landingPath } from "../../auth";
import { loadSentProposalDetail, loadSentProposals, parseProposalId } from "../lib/sentProposals";
import type { SentProposal, SentProposalDetail } from "../lib/sentProposals";

export type SentProposalsLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; proposals: SentProposal[] };

export type SentProposalDetailLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "notFound" }
  | { status: "loaded"; proposal: SentProposalDetail };

/**
 * 내가 보낸 제안 목록 (GET /me/proposals, 최신순). 내 활동 · 홈 · 내 정보가 함께 쓴다.
 * 실패하면 reload 로 다시 불러온다. 401 은 /login, 403 PROPOSAL_403_LIST_STUDENT 는 알림 뒤
 * landingPath() 로 보낸다 (그동안은 loading). 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useSentProposals(): { load: SentProposalsLoad; reload: () => void } {
  const navigate = useNavigate();
  const [load, setLoad] = useState<SentProposalsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    void loadSentProposals().then((result) => {
      if (!active) return;
      if (result.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (result.status === "forbidden") {
        window.alert("학생만 보낸 제안을 볼 수 있어요");
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

/**
 * 보낸 제안 하나 (GET /proposals/{id}). 주소의 id 가 숫자가 아니면 요청하지 않고 notFound.
 * 401 은 /login 으로 보낸다. 다른 id 로 바뀌거나 reload 하면 응답이 올 때까지 loading 이다.
 */
export function useSentProposalDetail(proposalId: string | undefined): {
  load: SentProposalDetailLoad;
  reload: () => void;
} {
  const navigate = useNavigate();
  const id = parseProposalId(proposalId);
  const [request, setRequest] = useState(0);
  // 어느 요청(id · 다시 시도 횟수)의 결과인지 함께 둬서 지난 결과를 보이지 않는다
  const key = `${id}:${request}`;
  const [result, setResult] = useState<{ key: string; load: SentProposalDetailLoad }>();

  useEffect(() => {
    if (id === undefined) return;
    let active = true;
    void loadSentProposalDetail(id).then((loaded) => {
      if (!active) return;
      if (loaded.status === "unauthorized") {
        navigate("/login", { replace: true });
        return;
      }
      setResult({ key, load: loaded.status === "loaded" ? loaded : { status: loaded.status } });
    });
    return () => {
      active = false;
    };
  }, [id, key, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);

  const load: SentProposalDetailLoad =
    id === undefined
      ? { status: "notFound" }
      : result?.key === key
        ? result.load
        : { status: "loading" };
  return { load, reload };
}

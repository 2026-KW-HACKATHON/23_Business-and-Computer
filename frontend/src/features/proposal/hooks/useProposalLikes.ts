import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../../../api/client";
import { sendProposalLike } from "../api/proposalLikeApi";

/** 제안 하나의 공감 수와 내가 공감했는지 */
export interface ProposalLike {
  likeCount: number;
  likedByMe: boolean;
}

/**
 * 공감 누르기 (POST · DELETE /proposals/{id}/likes). 누르면 바로 바뀌어 보이고, 서버 답으로 맞춘다.
 * 실패하면 누르기 전으로 되돌리고 failedId 에 남긴다. 보내는 중인 제안은 다시 눌러도 무시한다.
 * 401 은 /login 으로 보낸다. 화면을 떠난 뒤 온 답은 버린다.
 */
export function useProposalLikes(): {
  /** 이 화면에서 누른 결과가 있으면 그 값, 없으면 서버가 준 값 */
  likeOf: (proposalId: number, fromServer: ProposalLike) => ProposalLike;
  toggle: (proposalId: number, current: ProposalLike) => void;
  failedId: number | null;
} {
  const navigate = useNavigate();
  const [changed, setChanged] = useState<ReadonlyMap<number, ProposalLike>>(() => new Map());
  const [failedId, setFailedId] = useState<number | null>(null);
  const sending = useRef(new Set<number>());
  const active = useRef(true);

  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);

  const put = useCallback((proposalId: number, like: ProposalLike) => {
    setChanged((prev) => new Map(prev).set(proposalId, like));
  }, []);

  const likeOf = useCallback(
    (proposalId: number, fromServer: ProposalLike) => changed.get(proposalId) ?? fromServer,
    [changed],
  );

  const toggle = useCallback(
    (proposalId: number, current: ProposalLike) => {
      if (sending.current.has(proposalId)) return;
      sending.current.add(proposalId);
      const like = !current.likedByMe;
      setFailedId(null);
      put(proposalId, {
        likedByMe: like,
        likeCount: Math.max(0, current.likeCount + (like ? 1 : -1)),
      });
      sendProposalLike(proposalId, like)
        .then((answer) => {
          if (active.current) {
            put(proposalId, { likeCount: answer.likeCount, likedByMe: answer.likedByMe });
          }
        })
        .catch((error: unknown) => {
          if (!active.current) return;
          if (error instanceof ApiError && error.status === 401) {
            navigate("/login", { replace: true });
            return;
          }
          put(proposalId, current);
          setFailedId(proposalId);
        })
        .finally(() => sending.current.delete(proposalId));
    },
    [navigate, put],
  );

  return { likeOf, toggle, failedId };
}

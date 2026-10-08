import { useEffect, useState } from "react";
import { fetchMyProposals } from "../api/proposalApi";

/**
 * 보낸 제안(GET /me/proposals)에서 결제로 만들어진 의뢰 id. 이 안에 있으면 제안에서
 * 시작한 작업이고, 값은 그 제안 id 다 (제안서 화면). 불러오지 못하면 비어 있다 (의뢰로 본다)
 */
export function useProposalJobIds(): ReadonlyMap<number, number> {
  const [jobIds, setJobIds] = useState<ReadonlyMap<number, number>>(new Map());

  useEffect(() => {
    let active = true;
    void fetchMyProposals()
      .catch(() => [])
      .then((proposals) => {
        if (active) setJobIds(new Map(proposals.flatMap((p) => (p.jobId ? [[p.jobId, p.proposalId] as const] : []))));
      });
    return () => {
      active = false;
    };
  }, []);

  return jobIds;
}

import { useEffect, useState } from "react";
import { fetchMyProposals } from "../api/proposalApi";

/**
 * 보낸 제안(GET /me/proposals)에서 결제로 만들어진 의뢰 id. 이 안에 있으면 제안에서
 * 시작한 작업이다. 불러오지 못하면 빈 묶음 (의뢰로 본다)
 */
export function useProposalJobIds(): ReadonlySet<number> {
  const [jobIds, setJobIds] = useState<ReadonlySet<number>>(new Set());

  useEffect(() => {
    let active = true;
    void fetchMyProposals()
      .catch(() => [])
      .then((proposals) => {
        if (active) setJobIds(new Set(proposals.flatMap((p) => (p.jobId ? [p.jobId] : []))));
      });
    return () => {
      active = false;
    };
  }, []);

  return jobIds;
}

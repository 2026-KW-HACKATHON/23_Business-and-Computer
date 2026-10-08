import { EmpathyCount, WorkKindIcon } from "../../../components";
import type { ProposalLike } from "../../proposal";
import type { StudentPeerProposal } from "../types";
import "./PeerProposalRow.css";
import { studentTitle } from "../../../lib/korean";

interface PeerProposalRowProps {
  proposal: Pick<StudentPeerProposal, "title" | "studentName" | "storeName" | "status">;
  /** 지금 보이는 공감 수 · 공감 여부 (누른 결과 포함) */
  like: ProposalLike;
  onToggleLike: () => void;
  onOpen: () => void;
}

/**
 * 홈 「다른 학생들의 제안 공감하기」 한 줄 (GET /me/home 의 peerProposals, 내 제안은 빠져 있다).
 * 하트를 누르면 공감하고, 다시 누르면 취소한다 (수락 대기 제안만). 학생 이름은 studentName 을 줄 때만 보인다.
 */
function PeerProposalRow({ proposal, like, onToggleLike, onOpen }: PeerProposalRowProps) {
  return (
    <div className="peer-row">
      <button type="button" className="peer-row__open" onClick={onOpen}>
        <WorkKindIcon kind="proposal" size={24} />
        <span className="peer-row__text">
          <span className="peer-row__title">{proposal.title}</span>
          <span className="peer-row__meta">
            {proposal.studentName
              ? `${studentTitle(proposal.studentName)} → ${proposal.storeName}`
              : proposal.storeName}
          </span>
        </span>
      </button>
      <EmpathyCount
        count={like.likeCount}
        empathized={like.likedByMe}
        onToggle={!proposal.status || proposal.status === "PENDING" ? onToggleLike : undefined}
      />
    </div>
  );
}

export default PeerProposalRow;

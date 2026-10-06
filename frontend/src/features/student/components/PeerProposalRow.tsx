import { EmpathyCount, WorkKindIcon } from "../../../components";
import type { ExploreProposalCard } from "../../explore";
import "./PeerProposalRow.css";
import { studentTitle } from "../../../lib/korean";

interface PeerProposalRowProps {
  proposal: ExploreProposalCard;
  onOpen: () => void;
}

/**
 * 홈 「다른 학생들의 제안 공감하기」 한 줄 (GET /explore 의 제안). 공감은 수만 보인다.
 * 하트는 서버가 likedByMe 를 true 로 줄 때만 채워지고, 학생 이름은 studentName 을 줄 때만 보인다.
 */
function PeerProposalRow({ proposal, onOpen }: PeerProposalRowProps) {
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
      <EmpathyCount count={proposal.likeCount} empathized={proposal.likedByMe === true} />
    </div>
  );
}

export default PeerProposalRow;

import { EmpathyCount, WorkKindIcon } from "../../../components";
import type { PeerProposal } from "../types";
import "./PeerProposalRow.css";

interface PeerProposalRowProps {
  proposal: PeerProposal;
  onOpen: () => void;
  onToggleEmpathy: () => void;
}

/** 홈 「다른 학생들의 제안 공감하기」 한 줄. 하트로 바로 공감한다 */
function PeerProposalRow({ proposal, onOpen, onToggleEmpathy }: PeerProposalRowProps) {
  return (
    <div className="peer-row">
      <button type="button" className="peer-row__open" onClick={onOpen}>
        <WorkKindIcon kind="proposal" size={24} />
        <span className="peer-row__text">
          <span className="peer-row__title">{proposal.title}</span>
          <span className="peer-row__meta">
            {proposal.student.name} 학생 → {proposal.storeName}
          </span>
        </span>
      </button>
      <EmpathyCount
        count={proposal.empathyCount}
        empathized={proposal.empathized}
        onToggle={onToggleEmpathy}
      />
    </div>
  );
}

export default PeerProposalRow;

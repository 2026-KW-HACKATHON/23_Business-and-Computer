import { CategoryBadge, EmpathyCount, TextButton, WorkKindIcon } from "../../../components";
import { PEER_PROGRESS_LABEL } from "../lib/format";
import type { PeerProposal } from "../types";
import "./ExploreCards.css";

interface PeerProposalCardProps {
  proposal: PeerProposal;
  onOpen: () => void;
  onToggleEmpathy: () => void;
}

/**
 * 학생 탐색의 제안 카드. 수락을 기다리는 다른 학생 제안은 하트로 공감하고,
 * 수락된 제안과 내 제안은 공감 수만 보인다.
 */
function PeerProposalCard({ proposal, onOpen, onToggleEmpathy }: PeerProposalCardProps) {
  // 내 제안에는 공감할 수 없다 (공감 수만 보인다)
  const open = proposal.progress === "waitingAcceptance" && !proposal.mine;
  const hint = proposal.mine
    ? "내 제안이에요"
    : proposal.empathized
      ? "공감했어요"
      : open
        ? "하트를 눌러 공감"
        : "";

  return (
    <article className="student-card">
      <div className="student-card__head">
        <WorkKindIcon kind="proposal" size={22} />
        <button type="button" className="student-card__title" onClick={onOpen}>
          {proposal.title}
        </button>
        <EmpathyCount
          count={proposal.empathyCount}
          empathized={proposal.empathized}
          onToggle={open ? onToggleEmpathy : undefined}
        />
      </div>
      <div className="student-card__meta">
        <CategoryBadge field={proposal.field} />
        <span className="student-card__sub">
          {proposal.student.name} 학생 → {proposal.storeName}
        </span>
      </div>
      <p className="student-card__status">{PEER_PROGRESS_LABEL[proposal.progress]}</p>
      <p className="student-card__excerpt">{proposal.problem}</p>
      <div className="student-card__divider" />
      <div className="student-card__footer">
        <TextButton onClick={onOpen}>제안서 상세 보기</TextButton>
        {hint && <span className="student-card__hint">{hint}</span>}
      </div>
    </article>
  );
}

export default PeerProposalCard;

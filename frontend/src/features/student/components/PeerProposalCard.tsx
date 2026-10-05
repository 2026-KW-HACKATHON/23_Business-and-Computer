import { CategoryBadge, EmpathyCount, TextButton, WorkKindIcon } from "../../../components";
import { categoryNames } from "../../explore";
import type { ExploreProposalCard } from "../../explore";
import { sentProposalStatusLabel } from "../lib/sentProposals";
import "./ExploreCards.css";

interface PeerProposalCardProps {
  proposal: ExploreProposalCard;
  /** 내가 보낸 제안 (GET /me/proposals 에 있음) */
  mine: boolean;
  onOpen: () => void;
}

/**
 * 학생 탐색의 제안 카드 (GET /explore 의 PROPOSAL). 공감은 수만 보인다.
 * 하트는 서버가 likedByMe 를 true 로 줄 때만 채워지고, 학생 이름 · 상태 · 해결 미리보기는
 * 서버가 studentName · status · proposedSolution 을 줄 때만 보인다.
 */
function PeerProposalCard({ proposal, mine, onOpen }: PeerProposalCardProps) {
  return (
    <article className="student-card">
      <div className="student-card__head">
        <WorkKindIcon kind="proposal" size={22} />
        <button type="button" className="student-card__title" onClick={onOpen}>
          {proposal.title}
        </button>
        <EmpathyCount count={proposal.likeCount} empathized={proposal.likedByMe === true} />
      </div>
      <div className="student-card__meta">
        {categoryNames(proposal.specialtyCategories).map((name) => (
          <CategoryBadge key={name} field={name} />
        ))}
        <span className="student-card__sub">
          {proposal.studentName
            ? `${proposal.studentName} 학생 → ${proposal.storeName}`
            : proposal.storeName}
        </span>
      </div>
      {proposal.status && (
        <p className="student-card__status">{sentProposalStatusLabel(proposal.status)}</p>
      )}
      {proposal.proposedSolution && (
        <p className="student-card__excerpt">{proposal.proposedSolution}</p>
      )}
      <div className="student-card__divider" />
      <div className="student-card__footer">
        <TextButton onClick={onOpen}>제안서 상세 보기</TextButton>
        {mine && <span className="student-card__hint">내 제안이에요</span>}
      </div>
    </article>
  );
}

export default PeerProposalCard;

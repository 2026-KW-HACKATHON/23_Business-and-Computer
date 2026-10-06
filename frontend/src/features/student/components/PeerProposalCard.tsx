import { CategoryBadge, EmpathyCount, TextButton, WorkKindIcon } from "../../../components";
import { categoryNames } from "../../explore";
import type { ExploreProposalCard } from "../../explore";
import type { ProposalLike } from "../../proposal";
import { sentProposalStatusLabel } from "../lib/sentProposals";
import "./ExploreCards.css";
import { studentTitle } from "../../../lib/korean";

interface PeerProposalCardProps {
  proposal: ExploreProposalCard;
  /** 내가 보낸 제안 (GET /me/proposals 에 있음) */
  mine: boolean;
  /** 지금 보이는 공감 수 · 공감 여부 (누른 결과 포함) */
  like: ProposalLike;
  /** 하트를 눌렀을 때. 내 제안에는 없다 */
  onToggleLike?: () => void;
  onOpen: () => void;
}

/**
 * 학생 탐색의 제안 카드 (GET /explore 의 PROPOSAL). 하트를 누르면 공감하고, 다시 누르면 취소한다.
 * 내 제안은 하트를 누를 수 없고 「내 제안이에요」. 학생 이름 · 상태 · 해결 미리보기는
 * 서버가 studentName · status · proposedSolution 을 줄 때만 보인다.
 */
function PeerProposalCard({ proposal, mine, like, onToggleLike, onOpen }: PeerProposalCardProps) {
  return (
    <article className="student-card">
      <div className="student-card__head">
        <WorkKindIcon kind="proposal" size={22} />
        <button type="button" className="student-card__title" onClick={onOpen}>
          {proposal.title}
        </button>
        <EmpathyCount
          count={like.likeCount}
          empathized={like.likedByMe}
          onToggle={mine ? undefined : onToggleLike}
        />
      </div>
      <div className="student-card__meta">
        {categoryNames(proposal.specialtyCategories).map((name) => (
          <CategoryBadge key={name} field={name} />
        ))}
        <span className="student-card__sub">
          {proposal.studentName
            ? `${studentTitle(proposal.studentName)} → ${proposal.storeName}`
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
        <span className="student-card__hint">
          {mine ? "내 제안이에요" : like.likedByMe ? "공감했어요" : "하트를 눌러 공감"}
        </span>
      </div>
    </article>
  );
}

export default PeerProposalCard;

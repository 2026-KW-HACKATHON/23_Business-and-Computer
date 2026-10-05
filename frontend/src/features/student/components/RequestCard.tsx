import {
  Button,
  CategoryBadge,
  DeadlineBadge,
  TextButton,
  WorkKindIcon,
} from "../../../components";
import { formatMonthDay } from "../../../lib/date";
import { formatWon } from "../../../lib/money";
import type { StudentRequest } from "../types";
import "./ExploreCards.css";

interface RequestCardProps {
  request: StudentRequest;
  /** 이미 지원했으면 「지원하기」 대신 「지원했어요」 */
  applied: boolean;
  /** 제목 · 「의뢰서 전체 보기」 · 「의뢰서 상세 보기」: 의뢰서 전체 보기로 */
  onOpen: () => void;
  onApply: () => void;
}

/**
 * 학생 탐색의 의뢰 카드. 모집 중이면 예산 · 「의뢰서 전체 보기」 · 「지원하기」,
 * 끝났으면 「의뢰서 상세 보기」
 */
function RequestCard({ request, applied, onOpen, onApply }: RequestCardProps) {
  const recruiting = request.progress === "recruiting";

  return (
    <article className="student-card">
      <div className="student-card__head">
        <WorkKindIcon kind="request" size={22} />
        <button type="button" className="student-card__title" onClick={onOpen}>
          {request.title}
        </button>
        {recruiting && (
          <DeadlineBadge stage="draft" due={`${formatMonthDay(request.draftDue)}까지`} />
        )}
      </div>
      <div className="student-card__meta">
        <CategoryBadge field={request.field} />
        <span className="student-card__sub">{request.store.name}</span>
      </div>
      <p className="student-card__status">{recruiting ? "모집 중" : "완료"}</p>
      <div className="student-card__divider" />
      {recruiting ? (
        <>
          <div className="student-card__footer">
            <p className="student-card__budget">
              <span>예산</span>
              <strong>{formatWon(request.budget)}</strong>
            </p>
            <TextButton onClick={onOpen}>의뢰서 전체 보기</TextButton>
          </div>
          <Button
            tone="student"
            size="medium"
            fullWidth
            variant={applied ? "secondary" : "primary"}
            disabled={applied}
            onClick={onApply}
          >
            {applied ? "지원했어요" : "지원하기"}
          </Button>
        </>
      ) : (
        <TextButton className="student-card__open" onClick={onOpen}>
          의뢰서 상세 보기
        </TextButton>
      )}
    </article>
  );
}

export default RequestCard;
